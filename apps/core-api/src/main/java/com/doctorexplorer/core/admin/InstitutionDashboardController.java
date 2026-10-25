package com.doctorexplorer.core.admin;

import com.doctorexplorer.core.access.AccessService;
import com.doctorexplorer.core.audit.AuditLog;
import com.doctorexplorer.core.audit.AuditService;
import com.doctorexplorer.core.identity.web.SessionSupport;
import com.doctorexplorer.core.institution.domain.InstitutionMembership;
import com.doctorexplorer.core.institution.domain.InstitutionMembership.Role;
import com.doctorexplorer.core.institution.repo.InstitutionMemberships;
import com.doctorexplorer.core.institution.service.DirectoryService;
import com.doctorexplorer.core.institution.service.InstitutionTreePolicy;
import com.doctorexplorer.core.web.ApiProblemException;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * RF13 · painel de uma instituição e da árvore abaixo dela. Gestor do hospital/rede e auditor (papel efetivo
 * na árvore, RF12) com 2FA. Só o AUDITOR revela o id do paciente, e sempre com justificativa.
 */
@RestController
@RequestMapping("/api/v1/institutions/{id}/dashboard")
public class InstitutionDashboardController {

    private final MetricsService metrics;
    private final QualityService quality;
    private final AuditService audit;
    private final AccessService access;
    private final SessionSupport sessions;
    private final DirectoryService directory;
    private final InstitutionTreePolicy tree;
    private final InstitutionMemberships memberships;

    public InstitutionDashboardController(MetricsService metrics, QualityService quality, AuditService audit, AccessService access,
            SessionSupport sessions, DirectoryService directory, InstitutionTreePolicy tree, InstitutionMemberships memberships) {
        this.metrics = metrics;
        this.quality = quality;
        this.audit = audit;
        this.access = access;
        this.sessions = sessions;
        this.directory = directory;
        this.tree = tree;
        this.memberships = memberships;
    }

    @GetMapping("/engagement")
    Map<String, Object> engagement(@PathVariable UUID id, @RequestParam(defaultValue = "28") int days, HttpServletRequest request) {
        require(id, request);
        return metrics.engagement(members(id), days, AuditLog.scopeOf(id));
    }

    @GetMapping("/quality")
    Map<String, Object> quality(@PathVariable UUID id, HttpServletRequest request) {
        require(id, request);
        return quality.quality(members(id), false);
    }

    @GetMapping("/audit")
    Map<String, Object> search(@PathVariable UUID id, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID actorId, @RequestParam(required = false) String action,
            @RequestParam(required = false) String resourceType, @RequestParam(required = false) String justification,
            @RequestParam(defaultValue = "false") boolean revealPatient, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size, HttpServletRequest request) {
        Role role = require(id, request);
        return AuditViews.result(audit.search(viewer(id, role), AuditViews.query(from, to, actorId, action, resourceType, justification, revealPatient, page, size)));
    }

    @PostMapping("/audit/exports")
    ResponseEntity<Map<String, Object>> export(@PathVariable UUID id, @RequestBody PlatformDashboardController.ExportRequest r, HttpServletRequest request) {
        Role role = require(id, request);
        var x = audit.export(viewer(id, role), AuditViews.query(r.from(), r.to(), r.actorId(), r.action(), r.resourceType(), r.justification(), Boolean.TRUE.equals(r.revealPatient()), 0, 0));
        return ResponseEntity.status(HttpStatus.CREATED).body(AuditViews.export(x));
    }

    @GetMapping("/audit/exports")
    Map<String, Object> exports(@PathVariable UUID id, HttpServletRequest request) {
        Role role = require(id, request);
        return Map.of("exports", audit.myExports(viewer(id, role)).stream().map(AuditViews::export).toList());
    }

    /** Download confere de novo papel na árvore e 2FA; a visão (instituição + papel) precisa ser a da exportação. */
    @GetMapping("/audit/exports/{exportId}/download")
    Map<String, Object> download(@PathVariable UUID id, @PathVariable UUID exportId, HttpServletRequest request) {
        Role role = require(id, request);
        return AuditViews.download(audit.download(viewer(id, role), exportId));
    }

    /** Papel efetivo com leitura de auditoria (AUDITOR, gestor do hospital ou da rede) + 2FA; fora da árvore = 404. */
    private Role require(UUID institutionId, HttpServletRequest request) {
        UUID userId = sessions.require().userId();
        directory.get(institutionId);
        Role r = tree.effectiveRole(userId, institutionId).orElseThrow(() ->
                new ApiProblemException(HttpStatus.NOT_FOUND, "nao-encontrado", "Não encontrado", "A instituição não existe ou você não tem acesso."));
        if (!r.canViewAudit()) {
            throw new ApiProblemException(HttpStatus.FORBIDDEN, "sem-papel", "Sem acesso ao painel",
                    "O painel é para gestor do hospital ou da rede e para o auditor. Seu papel: " + r.name() + ".");
        }
        access.requireMfaSession(request);
        return r;
    }

    private AuditService.Viewer viewer(UUID institutionId, Role role) {
        List<String> scopes = tree.subtree(institutionId).stream().map(AuditLog::scopeOf).toList();
        return new AuditService.Viewer(sessions.require().userId(), "institution:" + institutionId + ":" + role.name(), null,
                role == Role.AUDITOR, scopes, institutionId);
    }

    private Set<UUID> members(UUID institutionId) {
        Set<UUID> out = new HashSet<>();
        for (UUID inst : tree.subtree(institutionId)) {
            memberships.findByInstitutionIdAndState(inst, InstitutionMembership.State.ACTIVE).forEach(m -> out.add(m.getUserId()));
        }
        return out;
    }
}
