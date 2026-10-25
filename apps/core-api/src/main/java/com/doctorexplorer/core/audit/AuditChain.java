package com.doctorexplorer.core.audit;

import com.doctorexplorer.core.common.Hashing;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/** Hash encadeado: SHA-256 de prevHash, seq e campos canônicos, em ordem fixa e separados por \u001f. */
public final class AuditChain {

    public static final String GENESIS = "0".repeat(64);

    private AuditChain() {}

    public static String hash(String prevHash, long seq, UUID id, Instant at, UUID actorId, String actorRoles, String action,
            String resourceType, String resourceId, String patientRef, String outcome, String justification, String ipHash,
            Map<String, String> details) {
        StringBuilder sb = new StringBuilder();
        for (Object part : new Object[] {prevHash, seq, id, at.toEpochMilli(), actorId, actorRoles, action, resourceType, resourceId,
                patientRef, outcome, justification, ipHash}) {
            sb.append(part == null ? "" : part).append('\u001f');
        }
        new TreeMap<>(details == null ? Map.of() : details).forEach((k, v) -> sb.append(k).append('=').append(v).append('\u001e'));
        return Hashing.sha256Hex(sb.toString());
    }

    public static String hashOf(AuditEvent e) {
        return hash(e.prevHash(), e.seq(), e.id(), e.at(), e.actorId(), e.actorRoles(), e.action(), e.resourceType(), e.resourceId(),
                e.patientRef(), e.outcome(), e.justification(), e.ipHash(), e.details());
    }

    /** Resultado da verificação de um trecho da cadeia já ordenado por seq. */
    public record Verification(boolean intact, Long firstBrokenSeq, String reason) {
        static Verification ok() {
            return new Verification(true, null, "");
        }
    }

    /**
     * Confere que cada evento recalcula o próprio hash e aponta para o anterior, sem buracos de seq. O primeiro
     * evento do trecho é a âncora (o anterior pode estar fora do período consultado), salvo se for o seq 1.
     */
    public static Verification verify(List<AuditEvent> ordered) {
        AuditEvent prev = null;
        for (AuditEvent e : ordered) {
            if (!hashOf(e).equals(e.hash())) return new Verification(false, e.seq(), "hash não confere: evento alterado");
            if (prev == null) {
                if (e.seq() == 1 && !GENESIS.equals(e.prevHash())) return new Verification(false, e.seq(), "início da cadeia inválido");
            } else {
                if (e.seq() != prev.seq() + 1) return new Verification(false, prev.seq() + 1, "evento ausente na sequência");
                if (!e.prevHash().equals(prev.hash())) return new Verification(false, e.seq(), "elo quebrado: prevHash diferente");
            }
            prev = e;
        }
        return Verification.ok();
    }
}
