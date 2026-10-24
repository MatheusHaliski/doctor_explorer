package com.doctorexplorer.core.config;

import com.doctorexplorer.core.credentialing.domain.ProfessionalProfile;
import com.doctorexplorer.core.credentialing.domain.UserRole;
import com.doctorexplorer.core.credentialing.repo.ProfessionalProfiles;
import com.doctorexplorer.core.credentialing.repo.UserRoles;
import com.doctorexplorer.core.identity.web.SessionSupport;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Só no perfil {@code dev}: atalhos para testar papéis e a fila de moderação sem mexer no banco à mão.
 * Nunca sobe em produção (o bean não existe fora de dev); a classe não tem estado nem segredo.
 */
@RestController
@Profile("dev")
@RequestMapping("/api/v1/dev")
public class DevController {

    private final UserRoles roles;
    private final ProfessionalProfiles profiles;
    private final SessionSupport sessions;

    public DevController(UserRoles roles, ProfessionalProfiles profiles, SessionSupport sessions) {
        this.roles = roles;
        this.profiles = profiles;
        this.sessions = sessions;
    }

    /** Concede um papel global à própria conta. Passa a valer no próximo login (os papéis são lidos ao autenticar). */
    @PostMapping("/roles/{role}")
    Map<String, Object> grantRole(@PathVariable String role) {
        UUID userId = sessions.require().userId();
        UserRole.Role r = UserRole.Role.valueOf(role.toUpperCase(Locale.ROOT));
        if (!roles.existsById(new UserRole.Key(userId, r))) roles.save(new UserRole(userId, r, null));
        return Map.of("userId", userId, "role", r.name(), "note", "Saia e entre de novo (com 2FA ativo) para o papel valer na sessão.");
    }

    /** Força o próprio selo para MANUAL_REVIEW (simula 24 h sem resposta da fonte do CFM). */
    @PostMapping("/credentials/manual-review")
    Map<String, Object> manualReview() {
        UUID userId = sessions.require().userId();
        ProfessionalProfile p = profiles.findById(userId).orElseGet(() -> new ProfessionalProfile(userId));
        p.markManualReview();
        profiles.save(p);
        return Map.of("seal", p.getSeal().name());
    }
}
