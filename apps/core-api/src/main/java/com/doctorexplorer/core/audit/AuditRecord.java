package com.doctorexplorer.core.audit;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** O que se pede para registrar. Construído com {@link #of(String)} e os métodos encadeados. */
public final class AuditRecord {

    final String action;
    UUID actorId;
    String actorRoles = "";
    String resourceType = "";
    String resourceId = "";
    String patientRef;
    String outcome = "success";
    String justification = "";
    String ipHash = "";
    UUID institutionId;
    final Map<String, String> details = new LinkedHashMap<>();

    private AuditRecord(String action) {
        if (action == null || !action.matches("[a-z]+(\\.[a-z_]+)+")) throw new IllegalArgumentException("ação de auditoria inválida: " + action);
        this.action = action;
    }

    public static AuditRecord of(String action) {
        return new AuditRecord(action);
    }

    public AuditRecord actor(UUID id, String roles) {
        this.actorId = id;
        this.actorRoles = roles == null ? "" : roles;
        return this;
    }

    public AuditRecord resource(String type, Object id) {
        this.resourceType = type == null ? "" : type;
        this.resourceId = id == null ? "" : String.valueOf(id);
        return this;
    }

    public AuditRecord patient(String ref) {
        this.patientRef = ref;
        return this;
    }

    public AuditRecord outcome(String outcome) {
        this.outcome = outcome;
        return this;
    }

    public AuditRecord justification(String text) {
        this.justification = text == null ? "" : text;
        return this;
    }

    public AuditRecord ip(String ipHash) {
        this.ipHash = ipHash == null ? "" : ipHash;
        return this;
    }

    /** Evento de uma instituição: além da cadeia da plataforma, entra na cadeia dela (auditoria da árvore). */
    public AuditRecord institution(UUID institutionId) {
        this.institutionId = institutionId;
        return this;
    }

    public AuditRecord detail(String key, Object value) {
        if (value != null) details.put(key, String.valueOf(value));
        return this;
    }
}
