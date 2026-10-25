package com.doctorexplorer.core.admin;

import com.doctorexplorer.core.access.AccessService;
import com.doctorexplorer.core.audit.AuditLog;
import com.doctorexplorer.core.audit.AuditService;
import com.doctorexplorer.core.identity.web.SessionSupport;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * RF13 · painel da plataforma em /api/v1/admin: papel MODERATOR, ADMIN ou DPO + 2FA (SecurityConfig).
 * Recortes: ADMIN vê toda a auditoria sem o id do paciente; DPO vê tudo e pode revelar o paciente com
 * justificativa; MODERATOR vê só moderação e comunidade.
 */
@RestController
@RequestMapping("/api/v1/admin")
public class PlatformDashboardController {

    /** {@code revealPatient} é Boolean: ausente no JSON = false (Jackson 3 recusa nulo em primitivo). */
    public record ExportRequest(LocalDate from, LocalDate to, UUID actorId, String action, String resourceType, String justification, Boolean revealPatient) {}

    private final MetricsService metrics;
    private final QualityService quality;
    private final AuditService audit;
    private final AccessService access;
    private final SessionSupport sessions;

    public PlatformDashboardController(MetricsService metrics, QualityService quality, AuditService audit, AccessService access, SessionSupport sessions) {
        this.metrics = metrics;
        this.quality = quality;
        this.audit = audit;
        this.access = access;
        this.sessions = sessions;
    }

    @GetMapping("/dashboard/engagement")
    Map<String, Object> engagement(@RequestParam(defaultValue = "28") int days) {
        return metrics.engagement(null, days, AuditLog.PLATFORM);
    }

    @GetMapping("/dashboard/quality")
    Map<String, Object> quality() {
        Set<String> roles = access.globalRoles();
        return quality.quality(null, roles.contains("DPO") || roles.contains("ADMIN"));
    }

    @GetMapping("/audit")
    Map<String, Object> search(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID actorId, @RequestParam(required = false) String action,
            @RequestParam(required = false) String resourceType, @RequestParam(required = false) String justification,
            @RequestParam(defaultValue = "false") boolean revealPatient, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return AuditViews.result(audit.search(viewer(), AuditViews.query(from, to, actorId, action, resourceType, justification, revealPatient, page, size)));
    }

    @PostMapping("/audit/exports")
    ResponseEntity<Map<String, Object>> export(@RequestBody ExportRequest r) {
        var x = audit.export(viewer(), AuditViews.query(r.from(), r.to(), r.actorId(), r.action(), r.resourceType(), r.justification(), Boolean.TRUE.equals(r.revealPatient()), 0, 0));
        return ResponseEntity.status(HttpStatus.CREATED).body(AuditViews.export(x));
    }

    private AuditService.Viewer viewer() {
        Set<String> roles = access.globalRoles();
        UUID userId = sessions.require().userId();
        if (roles.contains("DPO")) return new AuditService.Viewer(userId, "platform:dpo", null, true, List.of(AuditLog.PLATFORM), null);
        if (roles.contains("ADMIN")) return new AuditService.Viewer(userId, "platform:admin", null, false, List.of(AuditLog.PLATFORM), null);
        return new AuditService.Viewer(userId, "platform:moderator", Set.of("moderation", "community"), false, List.of(AuditLog.PLATFORM), null);
    }
}
