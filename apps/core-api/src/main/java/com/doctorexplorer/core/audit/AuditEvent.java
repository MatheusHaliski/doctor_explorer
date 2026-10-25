package com.doctorexplorer.core.audit;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * RF13 · um evento de auditoria já gravado. {@code patientRef} é o id opaco do paciente no cofre clínico
 * (nunca nome); só aparece para AUDITOR/DPO com justificativa (ver {@link AuditService#search}).
 */
public record AuditEvent(String scope, LocalDate day, long seq, UUID id, Instant at, UUID actorId, String actorRoles,
        String action, String resourceType, String resourceId, String patientRef, String outcome, String justification,
        String ipHash, Map<String, String> details, String prevHash, String hash) {

    /** Categoria = prefixo da ação (auth, privacy, moderation, institution, clinical, audit, media, chat, call). */
    public String category() {
        int dot = action.indexOf('.');
        return dot < 0 ? action : action.substring(0, dot);
    }

    public AuditEvent withPatientRef(String ref) {
        return new AuditEvent(scope, day, seq, id, at, actorId, actorRoles, action, resourceType, resourceId, ref, outcome,
                justification, ipHash, details, prevHash, hash);
    }
}
