package com.doctorexplorer.core.audit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuditChainTest {

    private static List<AuditEvent> chain(int n) {
        List<AuditEvent> out = new ArrayList<>();
        String prev = AuditChain.GENESIS;
        for (int i = 1; i <= n; i++) {
            UUID id = UUID.randomUUID();
            Instant at = Instant.parse("2026-10-25T10:00:00Z").plusSeconds(i);
            Map<String, String> details = Map.of("k", "v" + i);
            String h = AuditChain.hash(prev, i, id, at, null, "MODERATOR", "moderation.seal_approve", "user_account", "u" + i, null, "success", "", "", details);
            out.add(new AuditEvent("platform", LocalDate.of(2026, 10, 25), i, id, at, null, "MODERATOR", "moderation.seal_approve", "user_account", "u" + i,
                    null, "success", "", "", details, prev, h));
            prev = h;
        }
        return out;
    }

    @Test
    void intactChainVerifies() {
        assertThat(AuditChain.verify(chain(5)).intact()).isTrue();
        assertThat(AuditChain.verify(chain(5).subList(2, 5)).intact()).as("trecho no meio também confere").isTrue();
    }

    @Test
    void detectsTamperingGapsAndBrokenLinks() {
        List<AuditEvent> c = chain(5);
        AuditEvent e = c.get(2);
        c.set(2, new AuditEvent(e.scope(), e.day(), e.seq(), e.id(), e.at(), e.actorId(), e.actorRoles(), "moderation.seal_reject", e.resourceType(),
                e.resourceId(), e.patientRef(), e.outcome(), e.justification(), e.ipHash(), e.details(), e.prevHash(), e.hash()));
        var v = AuditChain.verify(c);
        assertThat(v.intact()).isFalse();
        assertThat(v.firstBrokenSeq()).isEqualTo(3);
        assertThat(v.reason()).contains("alterado");

        List<AuditEvent> gap = chain(5);
        gap.remove(3);
        assertThat(AuditChain.verify(gap).reason()).contains("ausente");

        // evento recalculado com hash próprio válido, mas apontando para outro anterior
        List<AuditEvent> forged = chain(3);
        AuditEvent f = forged.get(2);
        String fakePrev = "f".repeat(64);
        String h = AuditChain.hash(fakePrev, f.seq(), f.id(), f.at(), f.actorId(), f.actorRoles(), f.action(), f.resourceType(), f.resourceId(),
                f.patientRef(), f.outcome(), f.justification(), f.ipHash(), f.details());
        forged.set(2, new AuditEvent(f.scope(), f.day(), f.seq(), f.id(), f.at(), f.actorId(), f.actorRoles(), f.action(), f.resourceType(), f.resourceId(),
                f.patientRef(), f.outcome(), f.justification(), f.ipHash(), f.details(), fakePrev, h));
        assertThat(AuditChain.verify(forged).reason()).contains("elo quebrado");
    }

    @Test
    void detailsOrderDoesNotChangeTheHash() {
        UUID id = UUID.randomUUID();
        Instant at = Instant.now();
        String a = AuditChain.hash(AuditChain.GENESIS, 1, id, at, null, "", "audit.view", "", "", null, "success", "", "", Map.of("a", "1", "b", "2"));
        var m = new java.util.LinkedHashMap<String, String>();
        m.put("b", "2");
        m.put("a", "1");
        String b = AuditChain.hash(AuditChain.GENESIS, 1, id, at, null, "", "audit.view", "", "", null, "success", "", "", m);
        assertThat(a).isEqualTo(b);
    }

    @Test
    void csvEscapesFormulasAndQuotes() {
        assertThat(AuditService.csv("=HYPERLINK(\"x\")")).isEqualTo("\"'=HYPERLINK(\"\"x\"\")\"");
        assertThat(AuditService.csv("+55")).isEqualTo("\"'+55\"");
        assertThat(AuditService.csv("texto, com vírgula")).isEqualTo("\"texto, com vírgula\"");
        assertThat(AuditService.csv("simples")).isEqualTo("simples");
        assertThat(AuditService.csv(null)).isEmpty();
    }

    @Test
    void recordRejectsMalformedActions() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> AuditRecord.of("Login")).isInstanceOf(IllegalArgumentException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> AuditRecord.of("auth")).isInstanceOf(IllegalArgumentException.class);
        assertThat(AuditRecord.of("auth.mfa_enable")).isNotNull();
    }
}
