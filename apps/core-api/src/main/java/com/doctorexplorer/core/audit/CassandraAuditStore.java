package com.doctorexplorer.core.audit;

import com.datastax.oss.driver.api.core.ConsistencyLevel;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Component;

/**
 * Gravação append-only na Cassandra. Para anexar: lê a cabeça do escopo (SERIAL), grava o evento com seq+1 e
 * avança a cabeça por LWT ({@code IF seq = ?}). Se outra instância avançou antes, o evento provisório é
 * apagado e a operação recomeça. Nada no código atualiza ou apaga evento já encadeado.
 */
@Component
public class CassandraAuditStore {

    static final int MAX_ATTEMPTS = 20;

    private final CqlSession session;
    private final CassandraSchema schema;
    private volatile Statements st;

    private record Statements(PreparedStatement readHead, PreparedStatement insertHead, PreparedStatement advanceHead,
            PreparedStatement insertEvent, PreparedStatement deleteEvent, PreparedStatement selectDay) {}

    public CassandraAuditStore(CqlSession session, CassandraSchema schema) {
        this.session = session;
        this.schema = schema;
    }

    private Statements st() {
        if (st == null) {
            synchronized (this) {
                if (st == null) {
                    schema.ensure();
                    String ks = schema.keyspace();
                    st = new Statements(
                            session.prepare("SELECT seq, hash FROM " + ks + ".audit_chain_head WHERE scope = ?"),
                            session.prepare("INSERT INTO " + ks + ".audit_chain_head (scope, seq, hash, day) VALUES (?, ?, ?, ?) IF NOT EXISTS"),
                            session.prepare("UPDATE " + ks + ".audit_chain_head SET seq = ?, hash = ?, day = ? WHERE scope = ? IF seq = ?"),
                            session.prepare("INSERT INTO " + ks + ".audit_event (scope, day, seq, id, at, actor_id, actor_roles, action, resource_type, "
                                    + "resource_id, patient_ref, outcome, justification, ip_hash, details, prev_hash, hash) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"),
                            session.prepare("DELETE FROM " + ks + ".audit_event WHERE scope = ? AND day = ? AND seq = ? AND id = ?"),
                            session.prepare("SELECT * FROM " + ks + ".audit_event WHERE scope = ? AND day = ?"));
                }
            }
        }
        return st;
    }

    /** Anexa o registro à cadeia do escopo e devolve o evento gravado. */
    public AuditEvent append(String scope, AuditRecord r, UUID id, Instant at) {
        Statements s = st();
        LocalDate day = at.atZone(ZoneOffset.UTC).toLocalDate();
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            Row head = session.execute(s.readHead().bind(scope).setSerialConsistencyLevel(ConsistencyLevel.SERIAL)
                    .setConsistencyLevel(ConsistencyLevel.SERIAL)).one();
            long prevSeq = head == null ? 0 : head.getLong("seq");
            String prevHash = head == null ? AuditChain.GENESIS : head.getString("hash");
            long seq = prevSeq + 1;
            Map<String, String> details = Map.copyOf(r.details);
            String hash = AuditChain.hash(prevHash, seq, id, at, r.actorId, r.actorRoles, r.action, r.resourceType, r.resourceId,
                    r.patientRef, r.outcome, r.justification, r.ipHash, details);
            session.execute(s.insertEvent().bind(scope, day, seq, id, at, r.actorId, r.actorRoles, r.action, r.resourceType, r.resourceId,
                    r.patientRef, r.outcome, r.justification, r.ipHash, details, prevHash, hash).setConsistencyLevel(ConsistencyLevel.QUORUM));
            BoundStatement cas = head == null ? s.insertHead().bind(scope, seq, hash, day) : s.advanceHead().bind(seq, hash, day, scope, prevSeq);
            ResultSet rs = session.execute(cas.setSerialConsistencyLevel(ConsistencyLevel.SERIAL).setConsistencyLevel(ConsistencyLevel.QUORUM));
            if (rs.wasApplied()) {
                return new AuditEvent(scope, day, seq, id, at, r.actorId, r.actorRoles, r.action, r.resourceType, r.resourceId, r.patientRef,
                        r.outcome, r.justification, r.ipHash, details, prevHash, hash);
            }
            // outra gravação venceu a corrida: desfaz o provisório e tenta de novo
            session.execute(s.deleteEvent().bind(scope, day, seq, id));
            try {
                Thread.sleep(ThreadLocalRandom.current().nextInt(2, 15));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new IllegalStateException("Não foi possível encadear o evento de auditoria em " + scope);
    }

    /** Eventos do escopo nos dias do intervalo [from, to], em ordem de seq. */
    public List<AuditEvent> range(String scope, LocalDate from, LocalDate to) {
        Statements s = st();
        List<AuditEvent> out = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            for (Row row : session.execute(s.selectDay().bind(scope, d))) out.add(map(row));
        }
        out.sort((a, b) -> Long.compare(a.seq(), b.seq()));
        return out;
    }

    public Optional<Long> headSeq(String scope) {
        Row head = session.execute(st().readHead().bind(scope).setConsistencyLevel(ConsistencyLevel.SERIAL)).one();
        return head == null ? Optional.empty() : Optional.of(head.getLong("seq"));
    }

    private static AuditEvent map(Row r) {
        return new AuditEvent(r.getString("scope"), r.getLocalDate("day"), r.getLong("seq"), r.getUuid("id"), r.getInstant("at"),
                r.getUuid("actor_id"), r.getString("actor_roles"), r.getString("action"), r.getString("resource_type"), r.getString("resource_id"),
                r.getString("patient_ref"), r.getString("outcome"), r.getString("justification"), r.getString("ip_hash"),
                r.getMap("details", String.class, String.class), r.getString("prev_hash"), r.getString("hash"));
    }
}
