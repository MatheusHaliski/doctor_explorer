package com.doctorexplorer.core.calls;

import com.doctorexplorer.core.audit.AuditLog;
import com.doctorexplorer.core.audit.AuditRecord;
import com.doctorexplorer.core.common.UuidV7;
import com.doctorexplorer.core.credentialing.service.CredentialingService;
import com.doctorexplorer.core.internal.InternalTokens;
import com.doctorexplorer.core.web.ApiExceptionHandler.FieldProblem;
import com.doctorexplorer.core.web.ApiProblemException;
import com.doctorexplorer.core.web.ValidationProblemException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.sql.DataSource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * RF07 · chamadas de vídeo entre membros verificados de uma conversa do chat (RF06). A core-api só convida, admite
 * no SFU (token interno de 60 s com a sala e o aparelho), entrega credencial TURN efêmera e guarda metadados. A
 * mídia é cifrada quadro a quadro no aparelho com a chave exportada do grupo MLS da conversa: o SFU encaminha sem
 * conseguir abrir, e nada é gravado.
 */
@Service
public class CallService {

    public static final Set<String> QUALITIES = Set.of("BOA", "REGULAR", "RUIM");

    public record Participant(UUID userId, String name, UUID deviceId, Instant joinedAt, Instant leftAt) {}

    public record CallView(UUID id, UUID conversationId, String conversationKind, String conversationTitle, UUID startedBy, String startedByName, String state,
            Instant createdAt, Instant startedAt, Instant endedAt, List<Participant> participants) {}

    public record Admission(String token, String url, List<Map<String, Object>> iceServers, int maxParticipants) {}

    public record Joined(CallView call, Admission admission) {}

    private final JdbcClient db;
    private final TransactionTemplate tx;
    private final CallProperties props;
    private final InternalTokens tokens;
    private final CredentialingService credentialing;
    private final AuditLog audit;

    public CallService(DataSource dataSource, PlatformTransactionManager txm, CallProperties props, InternalTokens tokens, CredentialingService credentialing,
            AuditLog audit) {
        this.db = JdbcClient.create(dataSource);
        this.tx = new TransactionTemplate(txm);
        this.props = props;
        this.tokens = tokens;
        this.credentialing = credentialing;
        this.audit = audit;
    }

    /** Começa (ou entra na chamada já aberta da) conversa. */
    public Joined start(UUID userId, String userName, UUID conversationId, UUID deviceId) {
        requireMember(userId, conversationId);
        requireDevice(userId, deviceId);
        long members = db.sql("select count(*) from conversation_member where conversation_id = :c and left_at is null").param("c", conversationId)
                .query(Long.class).single();
        if (members > props.maxParticipants()) {
            throw invalid("conversationId", "Chamadas no MVP têm até " + props.maxParticipants() + " participantes; esta conversa tem " + members + ".");
        }
        UUID callId = tx.execute(s -> {
            db.sql("select id from conversation where id = :c for update").param("c", conversationId).query(UUID.class).single();
            expireRinging();
            Optional<UUID> open = db.sql("select id from call_session where conversation_id = :c and state in ('RINGING', 'ACTIVE')").param("c", conversationId)
                    .query(UUID.class).optional();
            if (open.isPresent()) return open.get();
            UUID id = UuidV7.generate();
            db.sql("insert into call_session (id, conversation_id, started_by) values (:id, :c, :u)").param("id", id).param("c", conversationId).param("u", userId)
                    .update();
            audit.record(AuditRecord.of("call.start").actor(userId, "").resource("call", id).detail("conversation", conversationId));
            return id;
        });
        return join(userId, userName, callId, deviceId);
    }

    public Joined join(UUID userId, String userName, UUID callId, UUID deviceId) {
        requireDevice(userId, deviceId);
        CallView before = view(callId);
        requireMember(userId, before.conversationId());
        if (!before.state().equals("RINGING") && !before.state().equals("ACTIVE")) {
            throw new ApiProblemException(HttpStatus.GONE, "chamada-encerrada", "Chamada encerrada", "Esta chamada já terminou. Comece outra pela conversa.");
        }
        tx.executeWithoutResult(s -> {
            db.sql("select id from call_session where id = :id for update").param("id", callId).query(UUID.class).single();
            long inCall = db.sql("select count(*) from call_participant where call_id = :id and left_at is null and user_id <> :u").param("id", callId)
                    .param("u", userId).query(Long.class).single();
            if (inCall + 1 > props.maxParticipants()) throw conflict("chamada-cheia", "Chamada cheia", "A chamada já tem " + props.maxParticipants() + " pessoas.");
            db.sql("""
                    insert into call_participant (call_id, user_id, device_id) values (:c, :u, :d)
                    on conflict (call_id, user_id) do update set device_id = :d, joined_at = now(), left_at = null""")
                    .param("c", callId).param("u", userId).param("d", deviceId).update();
            long now = inCall + 1;
            db.sql("""
                    update call_session set max_participants = greatest(max_participants, :n),
                      state = case when :n >= 2 then 'ACTIVE' else state end,
                      started_at = case when :n >= 2 and started_at is null then now() else started_at end
                    where id = :id""").param("n", now).param("id", callId).update();
            audit.record(AuditRecord.of("call.join").actor(userId, "").resource("call", callId));
        });
        return new Joined(view(callId), admission(userId, userName, callId, deviceId));
    }

    public CallView leave(UUID userId, UUID callId, String quality, Boolean screenShared) {
        String q = quality == null || quality.isBlank() ? null : quality.strip().toUpperCase();
        if (q != null && !QUALITIES.contains(q)) throw invalid("quality", "Use BOA, REGULAR ou RUIM.");
        tx.executeWithoutResult(s -> {
            db.sql("select id from call_session where id = :id for update").param("id", callId).query(UUID.class).optional()
                    .orElseThrow(() -> notFound());
            int n = db.sql("update call_participant set left_at = now(), quality = coalesce(:q, quality) where call_id = :c and user_id = :u and left_at is null")
                    .param("q", q).param("c", callId).param("u", userId).update();
            if (n == 0) return;
            if (Boolean.TRUE.equals(screenShared)) db.sql("update call_session set screen_shared = true where id = :id").param("id", callId).update();
            long remaining = db.sql("select count(*) from call_participant where call_id = :c and left_at is null").param("c", callId).query(Long.class).single();
            if (remaining == 0) {
                // qualidade da chamada = a pior relatada (métrica de produto sem conteúdo)
                db.sql("""
                        update call_session set ended_at = now(),
                          state = case when state = 'RINGING' then 'MISSED' else 'ENDED' end,
                          quality = (select case when bool_or(quality = 'RUIM') then 'RUIM' when bool_or(quality = 'REGULAR') then 'REGULAR'
                                                 when bool_or(quality = 'BOA') then 'BOA' end from call_participant where call_id = :c)
                        where id = :c""").param("c", callId).update();
                audit.record(AuditRecord.of("call.end").actor(userId, "").resource("call", callId));
            }
        });
        return view(callId);
    }

    public CallView decline(UUID userId, UUID callId) {
        CallView c = view(callId);
        requireMember(userId, c.conversationId());
        if (c.state().equals("RINGING") && c.conversationKind().equals("DIRECT")) {
            db.sql("update call_session set state = 'DECLINED', ended_at = now() where id = :id and state = 'RINGING'").param("id", callId).update();
            db.sql("update call_participant set left_at = now() where call_id = :id and left_at is null").param("id", callId).update();
            audit.record(AuditRecord.of("call.decline").actor(userId, "").resource("call", callId));
        }
        return view(callId);
    }

    public CallView get(UUID userId, UUID callId) {
        CallView c = view(callId);
        requireMember(userId, c.conversationId());
        return c;
    }

    /** Chamadas tocando ou em andamento nas conversas do usuário, em que ele ainda não está. */
    public List<CallView> incoming(UUID userId) {
        expireRinging();
        List<UUID> ids = db.sql("""
                select s.id from call_session s join conversation_member m on m.conversation_id = s.conversation_id and m.user_id = :u and m.left_at is null
                where s.state in ('RINGING', 'ACTIVE') and s.started_by <> :u
                  and not exists (select 1 from call_participant p where p.call_id = s.id and p.user_id = :u and p.left_at is null)
                order by s.created_at desc limit 5""").param("u", userId).query(UUID.class).list();
        return ids.stream().map(this::view).toList();
    }

    @Scheduled(fixedDelayString = "${doctor-explorer.jobs.call-ring-ms:15000}")
    public void expireRinging() {
        db.sql("""
                update call_session set state = 'MISSED', ended_at = now()
                where state = 'RINGING' and created_at < now() - make_interval(secs => :s)""").param("s", props.ringSeconds()).update();
        db.sql("""
                update call_participant p set left_at = now() from call_session s
                where p.call_id = s.id and s.state = 'MISSED' and p.left_at is null""").update();
    }

    // ---------- internos ----------

    private Admission admission(UUID userId, String name, UUID callId, UUID deviceId) {
        return new Admission(tokens.issueRtc(userId, name, callId, deviceId), props.publicUrl(), iceServers(userId), props.maxParticipants());
    }

    /** coturn com REST API: usuário "validade:id" e senha HMAC-SHA1 do segredo compartilhado; vale 10 min. */
    List<Map<String, Object>> iceServers(UUID userId) {
        List<String> urls = props.turnUrls().stream().map(String::strip).filter(u -> !u.isEmpty()).toList();
        if (urls.isEmpty() || props.turnSecret().isBlank()) return List.of();
        String username = (Instant.now().getEpochSecond() + props.turnTtlSeconds()) + ":" + userId;
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(props.turnSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            String credential = Base64.getEncoder().encodeToString(mac.doFinal(username.getBytes(StandardCharsets.UTF_8)));
            Map<String, Object> server = new LinkedHashMap<>();
            server.put("urls", urls);
            server.put("username", username);
            server.put("credential", credential);
            return List.of(server);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException(e);
        }
    }

    private CallView view(UUID callId) {
        Map<String, Object> c = db.sql("""
                select s.id, s.conversation_id, v.kind, v.title, s.started_by, u.display_name, s.state, s.created_at, s.started_at, s.ended_at
                from call_session s join conversation v on v.id = s.conversation_id join user_account u on u.id = s.started_by where s.id = :id""")
                .param("id", callId).query().listOfRows().stream().findFirst().orElseThrow(CallService::notFound);
        List<Participant> participants = db.sql("""
                select p.user_id, u.display_name, p.device_id, p.joined_at, p.left_at from call_participant p join user_account u on u.id = p.user_id
                where p.call_id = :id order by p.joined_at""").param("id", callId)
                .query((rs, i) -> new Participant(rs.getObject("user_id", UUID.class), rs.getString("display_name"), rs.getObject("device_id", UUID.class),
                        ts(rs.getTimestamp("joined_at")), ts(rs.getTimestamp("left_at")))).list();
        return new CallView(callId, (UUID) c.get("conversation_id"), (String) c.get("kind"), (String) c.get("title"), (UUID) c.get("started_by"),
                (String) c.get("display_name"), (String) c.get("state"), instant(c.get("created_at")), instant(c.get("started_at")), instant(c.get("ended_at")),
                participants);
    }

    private void requireMember(UUID userId, UUID conversationId) {
        if (conversationId == null) throw invalid("conversationId", "Escolha a conversa.");
        boolean member = db.sql("select count(*) from conversation_member where conversation_id = :c and user_id = :u and left_at is null").param("c", conversationId)
                .param("u", userId).query(Long.class).single() > 0;
        if (!member) throw notFound();
        if (!credentialing.isVerified(userId)) {
            throw new ApiProblemException(HttpStatus.FORBIDDEN, "selo-necessario", "Não permitido", "Só profissionais com selo verificado fazem chamadas.");
        }
    }

    /** A chave E2EE vem do grupo MLS: só um aparelho aprovado do próprio usuário entra. */
    private void requireDevice(UUID userId, UUID deviceId) {
        if (deviceId == null) throw invalid("deviceId", "Ative as mensagens neste aparelho antes de chamar.");
        boolean ok = db.sql("select count(*) from messaging_device where id = :d and user_id = :u and state = 'APPROVED'").param("d", deviceId).param("u", userId)
                .query(Long.class).single() > 0;
        if (!ok) throw invalid("deviceId", "Este aparelho não está aprovado para conversas cifradas.");
    }

    private static Instant ts(Timestamp t) {
        return t == null ? null : t.toInstant();
    }

    private static Instant instant(Object o) {
        if (o == null) return null;
        if (o instanceof Timestamp t) return t.toInstant();
        if (o instanceof java.time.OffsetDateTime odt) return odt.toInstant();
        return (Instant) o;
    }

    private static ValidationProblemException invalid(String field, String message) {
        return new ValidationProblemException(List.of(new FieldProblem(field, message)));
    }

    private static ApiProblemException conflict(String type, String title, String detail) {
        return new ApiProblemException(HttpStatus.CONFLICT, type, title, detail);
    }

    private static ApiProblemException notFound() {
        return new ApiProblemException(HttpStatus.NOT_FOUND, "nao-encontrado", "Não encontrado", "A chamada não existe ou você não tem acesso.");
    }
}
