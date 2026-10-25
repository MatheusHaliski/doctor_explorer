package com.doctorexplorer.core.audit;

import com.doctorexplorer.core.common.Hashing;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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

    /** Cabeça gravada do escopo: último seq confirmado e seu hash. */
    public record Head(long seq, String hash) {}

    /** Cadeia confirmada (ordem de seq) e o resultado da verificação contra a cabeça. */
    public record Resolved(List<AuditEvent> events, Verification verification) {}

    /**
     * Separa a cadeia confirmada das linhas lidas. Parte da cabeça (seq e hash gravados por LWT) e anda para trás
     * seguindo prevHash: em cada seq vale só a linha cujo hash é o esperado. Assim ficam de fora as linhas
     * provisórias de quem perdeu a corrida (ou caiu antes de apagá-las) e as gravações ainda em curso (seq acima da
     * cabeça); e a falta do último evento, de um trecho final ou de tudo deixa de passar como "íntegra".
     * {@code lowestSeq} é o menor seq que a janela lida precisa conter (a cabeça, se ela caiu na janela); o início da
     * janela não tem âncora além do seq 1 (gênese) — para isso existem as exportações assinadas.
     */
    public static Resolved resolve(List<AuditEvent> rows, Head head, long lowestSeq) {
        if (head == null) {
            return new Resolved(List.of(), rows.isEmpty() ? Verification.ok()
                    : new Verification(false, rows.get(0).seq(), "eventos sem cabeça da cadeia gravada"));
        }
        Map<Long, List<AuditEvent>> bySeq = new HashMap<>();
        long minRead = Long.MAX_VALUE;
        for (AuditEvent e : rows) {
            bySeq.computeIfAbsent(e.seq(), k -> new ArrayList<>()).add(e);
            minRead = Math.min(minRead, e.seq());
        }
        long floor = Math.max(1, Math.min(lowestSeq, minRead));
        if (floor > head.seq()) return new Resolved(List.of(), Verification.ok()); // nada esperado nesta janela
        List<AuditEvent> chain = new ArrayList<>();
        String expected = head.hash();
        Verification result = Verification.ok();
        for (long s = head.seq(); s >= floor; s--) {
            AuditEvent hit = null;
            for (AuditEvent c : bySeq.getOrDefault(s, List.of())) if (c.hash().equals(expected)) hit = c;
            if (hit == null) {
                result = new Verification(false, s, s == head.seq() ? "último evento ausente: a cabeça da cadeia aponta para ele"
                        : "evento ausente ou alterado na sequência");
                // o resto da janela ainda é mostrado, mas sem a garantia da cadeia
                for (long r = s - 1; r >= floor; r--) chain.addAll(bySeq.getOrDefault(r, List.of()));
                break;
            }
            if (!hashOf(hit).equals(hit.hash())) {
                result = new Verification(false, s, "hash não confere: evento alterado");
            }
            chain.add(hit);
            expected = hit.prevHash();
            if (s == 1 && !GENESIS.equals(expected) && result.intact()) result = new Verification(false, 1L, "início da cadeia inválido");
        }
        Collections.reverse(chain);
        chain.sort((a, b) -> Long.compare(a.seq(), b.seq()));
        return new Resolved(chain, result);
    }
}
