package com.doctorexplorer.core.messaging;

import com.datastax.oss.driver.api.core.ConsistencyLevel;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.uuid.Uuids;
import com.doctorexplorer.core.audit.CassandraSchema;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * RF06 · parte do Delivery Service na Cassandra: bytes MLS opacos (mensagens de aplicação e commits) por conversa,
 * na ordem aceita, e os Welcome de cada aparelho. Tudo com TTL; nada aqui é decifrável pelo servidor.
 */
@Component
public class CipherStore {

    public static final Duration WELCOME_TTL = Duration.ofDays(30);

    public record Envelope(long seq, UUID id, String kind, long epoch, UUID senderDevice, UUID senderUser, byte[] payload, Instant createdAt) {}

    public record Welcome(UUID id, UUID conversationId, long epoch, byte[] payload) {}

    private final CqlSession session;
    private final CassandraSchema schema;
    private volatile Statements st;

    private record Statements(PreparedStatement insertMessage, PreparedStatement selectAfter, PreparedStatement insertWelcome, PreparedStatement selectWelcomes,
            PreparedStatement deleteWelcome) {}

    public CipherStore(CqlSession session, CassandraSchema schema) {
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
                            session.prepare("INSERT INTO " + ks + ".mls_message (conversation_id, seq, id, kind, epoch, sender_device, sender_user, payload, created_at)"
                                    + " VALUES (?,?,?,?,?,?,?,?,?) USING TTL ?"),
                            session.prepare("SELECT * FROM " + ks + ".mls_message WHERE conversation_id = ? AND seq > ? LIMIT ?"),
                            session.prepare("INSERT INTO " + ks + ".mls_welcome (device_id, id, conversation_id, epoch, payload) VALUES (?,?,?,?,?) USING TTL ?"),
                            session.prepare("SELECT * FROM " + ks + ".mls_welcome WHERE device_id = ?"),
                            session.prepare("DELETE FROM " + ks + ".mls_welcome WHERE device_id = ? AND id = ?"));
                }
            }
        }
        return st;
    }

    public void append(UUID conversationId, Envelope e, Duration ttl) {
        session.execute(st().insertMessage().bind(conversationId, e.seq(), e.id(), e.kind(), e.epoch(), e.senderDevice(), e.senderUser(),
                ByteBuffer.wrap(e.payload()), e.createdAt(), (int) ttl.toSeconds()).setConsistencyLevel(ConsistencyLevel.QUORUM));
    }

    public List<Envelope> after(UUID conversationId, long afterSeq, int limit) {
        List<Envelope> out = new ArrayList<>();
        for (Row r : session.execute(st().selectAfter().bind(conversationId, afterSeq, limit).setConsistencyLevel(ConsistencyLevel.QUORUM))) {
            out.add(new Envelope(r.getLong("seq"), r.getUuid("id"), r.getString("kind"), r.getLong("epoch"), r.getUuid("sender_device"), r.getUuid("sender_user"),
                    bytes(r.getByteBuffer("payload")), r.getInstant("created_at")));
        }
        return out;
    }

    public UUID putWelcome(UUID deviceId, UUID conversationId, long epoch, byte[] payload) {
        UUID id = Uuids.timeBased();
        session.execute(st().insertWelcome().bind(deviceId, id, conversationId, epoch, ByteBuffer.wrap(payload), (int) WELCOME_TTL.toSeconds())
                .setConsistencyLevel(ConsistencyLevel.QUORUM));
        return id;
    }

    public List<Welcome> welcomes(UUID deviceId) {
        List<Welcome> out = new ArrayList<>();
        for (Row r : session.execute(st().selectWelcomes().bind(deviceId).setConsistencyLevel(ConsistencyLevel.QUORUM))) {
            out.add(new Welcome(r.getUuid("id"), r.getUuid("conversation_id"), r.getLong("epoch"), bytes(r.getByteBuffer("payload"))));
        }
        return out;
    }

    public void deleteWelcome(UUID deviceId, UUID id) {
        session.execute(st().deleteWelcome().bind(deviceId, id).setConsistencyLevel(ConsistencyLevel.QUORUM));
    }

    private static byte[] bytes(ByteBuffer b) {
        if (b == null) return new byte[0];
        byte[] out = new byte[b.remaining()];
        b.duplicate().get(out);
        return out;
    }
}
