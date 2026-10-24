package com.doctorexplorer.core.institution.web;

import com.doctorexplorer.core.access.AccessService;
import com.doctorexplorer.core.identity.repo.UserAccounts;
import com.doctorexplorer.core.identity.web.SessionSupport;
import com.doctorexplorer.core.institution.domain.Institution;
import com.doctorexplorer.core.institution.domain.InstitutionMembership;
import com.doctorexplorer.core.institution.domain.InstitutionMembership.Role;
import com.doctorexplorer.core.institution.repo.InstitutionChecks;
import com.doctorexplorer.core.institution.service.DirectoryService;
import com.doctorexplorer.core.institution.service.DirectoryService.NewInstitution;
import com.doctorexplorer.core.privacy.domain.ConsentPurpose;
import com.doctorexplorer.core.privacy.service.PrivacyService;
import com.doctorexplorer.core.web.ApiProblemException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * RF11 · /api/v1/institutions. Diretório público (visitante vê instituições VERIFICADA); vínculos,
 * papéis e corpo clínico exigem sessão e, para gestão, papel institucional na árvore.
 */
@RestController
@RequestMapping("/api/v1/institutions")
public class InstitutionController {

    public record RegisterRequest(String legalName, String tradeName, String cnpj, String cnes, String type, UUID parentId,
            String city, String uf, Double lat, Double lng) {}

    public record RoleRequest(String role) {}

    private final DirectoryService directory;
    private final InstitutionChecks checks;
    private final AccessService access;
    private final SessionSupport sessions;
    private final UserAccounts accounts;
    private final PrivacyService privacy;

    public InstitutionController(DirectoryService directory, InstitutionChecks checks, AccessService access, SessionSupport sessions,
            UserAccounts accounts, PrivacyService privacy) {
        this.directory = directory;
        this.checks = checks;
        this.access = access;
        this.sessions = sessions;
        this.accounts = accounts;
        this.privacy = privacy;
    }

    /** Público. Raio só com consentimento LOCATION ligado (RF10); sem ele, lat/lng são ignorados. */
    @GetMapping
    Map<String, Object> search(@RequestParam(required = false) String q, @RequestParam(required = false) String uf,
            @RequestParam(required = false) Double lat, @RequestParam(required = false) Double lng,
            @RequestParam(required = false, name = "radiusKm") Double radiusKm) {
        boolean locationAllowed = sessions.current().map(p -> privacy.consents(p.userId()).stream()
                .anyMatch(c -> c.purpose() == ConsentPurpose.LOCATION && c.granted())).orElse(false);
        Double useLat = locationAllowed ? lat : null;
        Double useLng = locationAllowed ? lng : null;
        List<Institution> found = directory.search(q, uf, useLat, useLng, locationAllowed ? radiusKm : null);
        return Map.of("items", found.stream().map(i -> publicView(i, useLat, useLng)).toList(), "count", found.size(),
                "locationUsed", locationAllowed && lat != null);
    }

    @GetMapping("/{id}")
    Map<String, Object> get(@PathVariable UUID id) {
        Institution i = directory.get(id);
        Map<String, Object> v = new LinkedHashMap<>(publicView(i, null, null));
        v.put("units", directory.children(id).stream().map(c -> Map.of("id", c.getId(), "name", c.getTradeName(), "type", c.getType().name())).toList());
        v.put("clinicalStaffCount", directory.clinicalStaff(id).size());
        sessions.current().ifPresent(p -> v.put("myRole", directory.effectiveRole(p.userId(), id).map(Role::name).orElse("NONE")));
        return v;
    }

    @PostMapping
    ResponseEntity<Map<String, Object>> register(@RequestBody RegisterRequest req) {
        UUID userId = access.requireActive().userId();
        Institution.Type type;
        try {
            type = req.type() == null ? null : Institution.Type.valueOf(req.type().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            type = null;
        }
        Institution i = directory.register(userId, new NewInstitution(req.legalName(), req.tradeName(), req.cnpj(), req.cnes(), type,
                req.parentId(), req.city(), req.uf(), req.lat(), req.lng()));
        Map<String, Object> v = new LinkedHashMap<>(publicView(i, null, null));
        v.put("state", i.getState().name());
        v.put("checks", checks.findByInstitutionIdOrderByCheckedAtDesc(i.getId()).stream().map(c -> Map.of(
                "source", c.getSource(), "outcome", c.getOutcome().name(), "reason", c.getReasonPtBr() == null ? "" : c.getReasonPtBr())).toList());
        return ResponseEntity.status(i.getState() == Institution.State.VERIFICADA ? HttpStatus.CREATED : HttpStatus.ACCEPTED).body(v);
    }

    /** Corpo clínico verificado: médicos VERIFIED com vínculo ativo. Visível para qualquer sessão. */
    @GetMapping("/{id}/staff")
    Map<String, Object> staff(@PathVariable UUID id) {
        sessions.require();
        directory.get(id);
        return Map.of("staff", directory.clinicalStaff(id).stream().map(m -> Map.of(
                "userId", m.getUserId(), "name", accounts.findById(m.getUserId()).map(a -> a.getDisplayName()).orElse("—"),
                "role", m.getRole().name(), "since", m.getSince().toString())).toList());
    }

    @PostMapping("/{id}/join")
    Map<String, Object> join(@PathVariable UUID id) {
        UUID userId = access.requireVerifiedSeal().userId();
        InstitutionMembership m = directory.requestOrAccept(userId, id);
        return Map.of("state", m.getState().name(), "role", m.getRole().name());
    }

    /** Gestão: lista todos os vínculos (inclusive pendentes) da instituição. Só gestores da árvore. */
    @GetMapping("/{id}/members")
    Map<String, Object> members(@PathVariable UUID id) {
        UUID userId = sessions.require().userId();
        Role r = directory.effectiveRole(userId, id).orElseThrow(() ->
                new ApiProblemException(HttpStatus.NOT_FOUND, "nao-encontrado", "Não encontrado", "A instituição não existe ou você não tem acesso."));
        if (!r.canManageMembers() && !r.canViewAudit()) {
            throw new ApiProblemException(HttpStatus.FORBIDDEN, "sem-papel", "Sem permissão de gestão", "Seu papel " + r.name() + " não vê a gestão de membros.");
        }
        return Map.of("members", directory.membershipsAll(id).stream().map(m -> Map.of("userId", m.getUserId(), "role", m.getRole().name(), "state", m.getState().name())).toList());
    }

    @PostMapping("/{id}/members/{userId}")
    Map<String, Object> inviteOrApprove(@PathVariable UUID id, @PathVariable UUID userId) {
        InstitutionMembership m = directory.inviteOrApprove(sessions.require().userId(), id, userId);
        return Map.of("userId", userId, "state", m.getState().name(), "role", m.getRole().name());
    }

    @PutMapping("/{id}/members/{userId}/role")
    Map<String, Object> assignRole(@PathVariable UUID id, @PathVariable UUID userId, @RequestBody RoleRequest req) {
        Role role;
        try {
            role = Role.valueOf(req.role().trim().toUpperCase(Locale.ROOT));
        } catch (RuntimeException e) {
            throw new ApiProblemException(HttpStatus.UNPROCESSABLE_CONTENT, "papel", "Papel desconhecido", "Papéis: " + List.of(Role.values()));
        }
        InstitutionMembership m = directory.assignRole(sessions.require().userId(), id, userId, role);
        return Map.of("userId", userId, "role", m.getRole().name());
    }

    @DeleteMapping("/{id}/members/{userId}")
    ResponseEntity<Void> end(@PathVariable UUID id, @PathVariable UUID userId) {
        directory.endMembership(sessions.require().userId(), id, userId);
        return ResponseEntity.noContent().build();
    }

    private static Map<String, Object> publicView(Institution i, Double lat, Double lng) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", i.getId());
        m.put("name", i.getTradeName());
        m.put("type", i.getType().name());
        m.put("city", i.getCity());
        m.put("uf", i.getUf());
        m.put("cnes", i.getCnes() == null ? "" : i.getCnes());
        m.put("verified", i.getState() == Institution.State.VERIFICADA);
        m.put("parentId", i.getParentId() == null ? "" : i.getParentId().toString());
        if (lat != null && lng != null && i.getLat() != null && i.getLng() != null) m.put("distanceKm", Math.round(haversine(lat, lng, i.getLat(), i.getLng()) * 10) / 10.0);
        return m;
    }

    static double haversine(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1), dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 6371 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
