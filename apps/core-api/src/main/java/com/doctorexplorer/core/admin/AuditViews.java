package com.doctorexplorer.core.admin;

import com.doctorexplorer.core.audit.AuditEvent;
import com.doctorexplorer.core.audit.AuditExport;
import com.doctorexplorer.core.audit.AuditService;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Conversões compartilhadas pelos controladores da auditoria (plataforma e instituição). */
final class AuditViews {

    private AuditViews() {}

    static AuditService.Query query(LocalDate from, LocalDate to, UUID actorId, String action, String resourceType, String justification,
            boolean revealPatient, int page, int size) {
        return new AuditService.Query(from, to, actorId, action, resourceType, justification, revealPatient, page, size);
    }

    static Map<String, Object> result(AuditService.Result r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("events", r.events().stream().map(AuditViews::event).toList());
        m.put("total", r.total());
        m.put("page", r.page());
        m.put("size", r.size());
        m.put("chain", Map.of("intact", r.chain().intact(), "broken", r.chain().broken(), "scopesChecked", r.chain().scopesChecked(),
                "eventsChecked", r.chain().eventsChecked()));
        m.put("patientRevealed", r.patientRevealed());
        return m;
    }

    static Map<String, Object> event(AuditEvent e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.id());
        m.put("seq", e.seq());
        m.put("scope", e.scope());
        m.put("at", e.at().toString());
        m.put("actorId", e.actorId() == null ? "" : e.actorId().toString());
        m.put("actorRoles", e.actorRoles());
        m.put("action", e.action());
        m.put("category", e.category());
        m.put("resourceType", e.resourceType());
        m.put("resourceId", e.resourceId());
        m.put("patientRef", e.patientRef() == null ? "" : e.patientRef());
        m.put("outcome", e.outcome());
        m.put("justification", e.justification());
        m.put("details", e.details());
        m.put("hash", e.hash());
        return m;
    }

    static Map<String, Object> export(AuditExport x) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", x.getId());
        m.put("state", x.getState().name());
        m.put("filters", x.getFilters());
        m.put("events", x.getEventCount());
        m.put("sha256", x.getSha256() == null ? "" : x.getSha256());
        m.put("signature", x.getSignature() == null ? "" : x.getSignature());
        m.put("createdAt", x.getCreatedAt().toString());
        m.put("expiresAt", x.getExpiresAt() == null ? "" : x.getExpiresAt().toString());
        m.put("downloadUrl", "/api/v1/audit/exports/" + x.getId() + "/download");
        return m;
    }
}
