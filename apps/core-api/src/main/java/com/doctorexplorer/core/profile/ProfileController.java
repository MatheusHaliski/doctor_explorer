package com.doctorexplorer.core.profile;

import com.doctorexplorer.core.access.AccessService;
import com.doctorexplorer.core.credentialing.domain.ProfessionalProfile;
import com.doctorexplorer.core.credentialing.domain.ProfessionalProfile.Visibility;
import com.doctorexplorer.core.credentialing.repo.ProfessionalProfiles;
import com.doctorexplorer.core.credentialing.repo.SpecialtyClaims;
import com.doctorexplorer.core.credentialing.service.CredentialingService;
import com.doctorexplorer.core.identity.domain.SealStatus;
import com.doctorexplorer.core.identity.domain.UserAccount;
import com.doctorexplorer.core.identity.repo.UserAccounts;
import com.doctorexplorer.core.identity.web.SessionPrincipal;
import com.doctorexplorer.core.identity.web.SessionSupport;
import com.doctorexplorer.core.institution.service.DirectoryService;
import com.doctorexplorer.core.web.ApiExceptionHandler.FieldProblem;
import com.doctorexplorer.core.web.ApiProblemException;
import com.doctorexplorer.core.web.ValidationProblemException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * RF03 · Perfil profissional. Visibilidade padrão "rede verificada"; PUBLIC_MINIMAL mostra só nome, CRM e
 * especialidades a visitantes; MY_INSTITUTION exige vínculo ativo em comum. Texto livre passa pelo
 * verificador de publicidade médica (Res. CFM 2.336/2023). Mudar o nome devolve o selo para análise.
 */
@RestController
@RequestMapping("/api/v1")
public class ProfileController {

    public record ProfileEdit(String displayName, String bio, String visibility) {}

    private final ProfessionalProfiles profiles;
    private final SpecialtyClaims claims;
    private final UserAccounts accounts;
    private final CredentialingService credentialing;
    private final DirectoryService directory;
    private final AccessService access;
    private final SessionSupport sessions;
    private final AdvertisingRuleChecker advertising;

    public ProfileController(ProfessionalProfiles profiles, SpecialtyClaims claims, UserAccounts accounts, CredentialingService credentialing,
            DirectoryService directory, AccessService access, SessionSupport sessions, AdvertisingRuleChecker advertising) {
        this.profiles = profiles;
        this.claims = claims;
        this.accounts = accounts;
        this.credentialing = credentialing;
        this.directory = directory;
        this.access = access;
        this.sessions = sessions;
        this.advertising = advertising;
    }

    @GetMapping("/profiles/{id}")
    Map<String, Object> view(@PathVariable UUID id) {
        UserAccount a = accounts.findById(id).filter(x -> x.getStatus() != com.doctorexplorer.core.identity.domain.AccountStatus.ANONYMOUS_LGPD_DELETED).orElseThrow(this::notFound);
        ProfessionalProfile p = profiles.findById(id).orElse(null);
        Visibility visibility = p == null ? Visibility.VERIFIED_NETWORK : p.getVisibility();
        Optional<SessionPrincipal> viewer = sessions.current();
        boolean self = viewer.map(v -> v.userId().equals(id)).orElse(false);
        boolean verifiedViewer = viewer.map(v -> credentialing.isVerified(v.userId())).orElse(false) || access.isPlatformModeratorSafe();
        boolean minimal;
        if (self || verifiedViewer) minimal = false;
        else if (visibility == Visibility.PUBLIC_MINIMAL) minimal = true;
        else throw notFound(); // sem acesso: 404, não revela existência
        if (!self && !minimal && visibility == Visibility.MY_INSTITUTION && !access.isPlatformModeratorSafe()) {
            Set<UUID> mine = directory.membershipsOf(viewer.get().userId()).stream().map(m -> m.getInstitutionId()).collect(Collectors.toSet());
            boolean shared = directory.membershipsOf(id).stream().anyMatch(m -> mine.contains(m.getInstitutionId()));
            if (!shared) throw notFound();
        }
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", a.getId());
        v.put("displayName", a.getDisplayName());
        v.put("seal", p == null ? SealStatus.NONE.name() : p.getSeal().name());
        v.put("crm", p == null || p.getCrmNumber() == null ? "" : "CRM-" + p.getCrmUf() + " " + p.getCrmNumber());
        v.put("specialties", claims.findByUserId(id).stream().filter(c -> c.isConfirmed()).map(c -> c.getSpecialtyCode()).toList());
        if (!minimal) {
            v.put("bio", p == null || p.getBio() == null ? "" : p.getBio());
            v.put("institutions", directory.membershipsOf(id).stream().map(m -> Map.of("institutionId", m.getInstitutionId(), "role", m.getRole().name())).toList());
            v.put("visibility", visibility.name());
        }
        return v;
    }

    @PutMapping("/me/profile")
    @Transactional
    Map<String, Object> edit(@RequestBody ProfileEdit edit) {
        UUID userId = access.requireActive().userId();
        UserAccount a = accounts.findById(userId).orElseThrow();
        ProfessionalProfile p = profiles.findById(userId).orElseGet(() -> new ProfessionalProfile(userId));
        List<FieldProblem> problems = new ArrayList<>();
        Visibility visibility = p.getVisibility();
        if (edit.visibility() != null) {
            try {
                visibility = Visibility.valueOf(edit.visibility().trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                problems.add(new FieldProblem("visibility", "Use VERIFIED_NETWORK, MY_INSTITUTION ou PUBLIC_MINIMAL."));
            }
        }
        if (edit.displayName() != null && (edit.displayName().trim().length() < 2 || edit.displayName().trim().length() > 120)) {
            problems.add(new FieldProblem("displayName", "O nome precisa ter de 2 a 120 caracteres."));
        }
        if (edit.bio() != null && edit.bio().length() > 1000) problems.add(new FieldProblem("bio", "A apresentação tem no máximo 1000 caracteres."));
        for (String f : advertising.check(edit.bio())) problems.add(new FieldProblem("bio", f));
        if (!problems.isEmpty()) throw new ValidationProblemException(problems);

        boolean sealReset = false;
        if (edit.displayName() != null && !edit.displayName().trim().equals(a.getDisplayName())) {
            a.rename(edit.displayName());
            accounts.save(a);
            if (p.getSeal() == SealStatus.VERIFIED) {
                p.onIdentityChanged();
                sealReset = true;
            }
        }
        p.edit(edit.bio() == null ? p.getBio() : edit.bio().trim(), visibility);
        profiles.save(p);
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("displayName", a.getDisplayName());
        v.put("bio", p.getBio() == null ? "" : p.getBio());
        v.put("visibility", p.getVisibility().name());
        v.put("seal", p.getSeal().name());
        if (sealReset) v.put("note", "Você mudou o nome: o selo voltou para análise e será conferido de novo na fonte do CFM.");
        return v;
    }

    private ApiProblemException notFound() {
        return new ApiProblemException(HttpStatus.NOT_FOUND, "nao-encontrado", "Não encontrado", "O perfil não existe ou você não tem acesso.");
    }
}
