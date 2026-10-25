package com.doctorexplorer.core.messaging;

import com.doctorexplorer.core.audit.AuditLog;
import com.doctorexplorer.core.audit.AuditRecord;
import com.doctorexplorer.core.common.UuidV7;
import com.doctorexplorer.core.community.service.IdentifierDetector;
import com.doctorexplorer.core.credentialing.service.CredentialingService;
import com.doctorexplorer.core.storage.ObjectStore;
import com.doctorexplorer.core.storage.StorageProperties;
import com.doctorexplorer.core.web.ApiExceptionHandler.FieldProblem;
import com.doctorexplorer.core.web.ApiProblemException;
import com.doctorexplorer.core.web.ValidationProblemException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * RF06 · Delivery Service e diretório de aparelhos do chat MLS (RFC 9420). O servidor ordena commits por época,
 * guarda e entrega bytes cifrados, distribui KeyPackages de uso único e diz quais aparelhos podem estar em cada
 * grupo; quem cifra, decifra e confere as credenciais é o cliente. Mensagem nunca é lida, registrada nem auditada
 * pelo conteúdo.
 */
@Service
public class MessagingService {

    public static final int MAX_GROUP_MEMBERS = 50;
    public static final int MAX_KEY_PACKAGES = 100;
    public static final int MAX_PAYLOAD = 256 * 1024;
    public static final long MAX_ATTACHMENT = 25L * 1024 * 1024;
    public static final Set<Integer> RETENTIONS = Set.of(1, 7, 90);
    static final String APPROVAL_CONTEXT = "doctor-explorer/device-approval/v1|";

    public record DeviceView(UUID id, UUID userId, String userName, String name, String signatureKey, String state, String approvedVia, Instant createdAt,
            Instant approvedAt, Instant lastSeenAt, int freeKeyPackages) {}

    public record MemberView(UUID userId, String name, String role, boolean verified, Instant joinedAt) {}

    public record PendingDevice(UUID deviceId, UUID userId, boolean hasKeyPackage) {}

    public record ConversationView(UUID id, String kind, String title, long epoch, int retentionDays, String state, Instant createdAt, Instant lastMessageAt,
            boolean unread, long lastReadSeq, List<MemberView> members, List<UUID> devices, List<PendingDevice> devicesToAdd, List<UUID> devicesToRemove) {}

    public record Summary(UUID id, String kind, String title, List<MemberView> members, Instant lastMessageAt, boolean unread, int retentionDays) {}

    public record ClaimedKeyPackage(UUID deviceId, UUID userId, String keyPackage) {}

    public record CommitInput(Long epoch, UUID senderDeviceId, String commit, String welcome, List<UUID> addedDeviceIds, List<UUID> removedDeviceIds) {}

    public record MessageInput(Long epoch, UUID senderDeviceId, String payload) {}

    public record EnvelopeView(long seq, String kind, long epoch, UUID senderDeviceId, UUID senderUserId, String payload, Instant createdAt) {}

    public record WelcomeView(UUID id, UUID conversationId, long epoch, String payload) {}

    private final JdbcClient db;
    private final TransactionTemplate tx;
    private final CipherStore ciphers;
    private final CredentialingService credentialing;
    private final PasswordEncoder encoder;
    private final AuditLog audit;
    private final IdentifierDetector detector;
    private final ObjectStore objects;
    private final StorageProperties storage;

    public MessagingService(DataSource dataSource, PlatformTransactionManager txm, CipherStore ciphers, CredentialingService credentialing,
            PasswordEncoder encoder, AuditLog audit, IdentifierDetector detector, ObjectStore objects, StorageProperties storage,
            com.doctorexplorer.core.media.MediaProperties media) {
        this.db = JdbcClient.create(dataSource);
        this.tx = new TransactionTemplate(txm);
        this.ciphers = ciphers;
        this.credentialing = credentialing;
        this.encoder = encoder;
        this.audit = audit;
        this.detector = detector;
        this.objects = objects;
        this.storage = storage;
        try {
            // o navegador envia e baixa o anexo já cifrado direto do S3 (mesma origem configurada para o RF05)
            objects.allowBrowserUploads(storage.buckets().chatAttachments(), media.corsOrigins());
        } catch (RuntimeException e) {
            org.slf4j.LoggerFactory.getLogger(MessagingService.class).warn("Não configurou CORS do bucket de anexos do chat: {}", e.toString());
        }
    }

    // ---------- aparelhos ----------

    /**
     * Primeiro aparelho da conta entra aprovado e precisa trazer o verificador da frase de recuperação; os seguintes
     * ficam pendentes até um aparelho confiável assinar a aprovação ou a frase ser apresentada.
     */
    public DeviceView registerDevice(UUID userId, String name, String signatureKeyB64, String recoveryVerifierB64) {
        byte[] key = decodeKey(signatureKeyB64, "signatureKey");
        String n = name == null ? "" : name.strip();
        if (n.isEmpty() || n.length() > 60) throw invalid("name", "Dê um nome de até 60 caracteres ao aparelho (ex.: Notebook do consultório).");
        return tx.execute(s -> {
            db.sql("select id from user_account where id = :u for update").param("u", userId).query(UUID.class).single();
            boolean first = db.sql("select count(*) from messaging_device where user_id = :u and state = 'APPROVED'").param("u", userId).query(Long.class).single() == 0;
            boolean hasRecovery = db.sql("select count(*) from messaging_recovery where user_id = :u").param("u", userId).query(Long.class).single() > 0;
            if (first && !hasRecovery) {
                byte[] verifier = decodeVerifier(recoveryVerifierB64);
                db.sql("insert into messaging_recovery (user_id, verifier_hash) values (:u, :h)").param("u", userId)
                        .param("h", encoder.encode(Base64.getEncoder().encodeToString(verifier))).update();
            }
            if (db.sql("select count(*) from messaging_device where signature_key = :k").param("k", key).query(Long.class).single() > 0) {
                throw new ApiProblemException(HttpStatus.CONFLICT, "aparelho-existente", "Aparelho já cadastrado", "Esta chave já pertence a um aparelho.");
            }
            UUID id = UuidV7.generate();
            boolean autoApprove = first && !hasRecovery;
            db.sql("""
                    insert into messaging_device (id, user_id, name, signature_key, state, approved_via, approved_at)
                    values (:id, :u, :n, :k, :st, :via, :at)""").param("id", id).param("u", userId).param("n", n).param("k", key)
                    .param("st", autoApprove ? "APPROVED" : "PENDING").param("via", autoApprove ? "FIRST" : null)
                    .param("at", autoApprove ? Timestamp.from(Instant.now()) : null).update();
            audit.record(AuditRecord.of("messaging.device_register").actor(userId, "").resource("messaging_device", id).detail("state", autoApprove ? "APPROVED" : "PENDING"));
            return device(id).orElseThrow();
        });
    }

    /** Um aparelho já aprovado assina "doctor-explorer/device-approval/v1|{novo}|{chave do novo}" com a própria chave. */
    public DeviceView approveDevice(UUID userId, UUID deviceId, UUID approverDeviceId, String signatureB64) {
        return tx.execute(s -> {
            DeviceRow target = ownDevice(userId, deviceId);
            if (!target.state().equals("PENDING")) throw conflict("aparelho-nao-pendente", "Aparelho não está pendente", "Só um aparelho pendente pode ser aprovado.");
            DeviceRow approver = ownDevice(userId, approverDeviceId);
            if (!approver.state().equals("APPROVED")) throw forbidden("aparelho-nao-confiavel", "Aprove a partir de um aparelho já confiável.");
            byte[] message = (APPROVAL_CONTEXT + deviceId + "|" + Base64.getEncoder().encodeToString(target.key())).getBytes(StandardCharsets.UTF_8);
            if (!verifyEd25519(approver.key(), message, signatureB64)) throw forbidden("assinatura-invalida", "A assinatura da aprovação não confere.");
            db.sql("update messaging_device set state = 'APPROVED', approved_via = 'DEVICE', approved_by = :by, approved_at = now() where id = :id")
                    .param("by", approverDeviceId).param("id", deviceId).update();
            audit.record(AuditRecord.of("messaging.device_approve").actor(userId, "").resource("messaging_device", deviceId).detail("via", "DEVICE"));
            return device(deviceId).orElseThrow();
        });
    }

    public DeviceView recoverDevice(UUID userId, UUID deviceId, String verifierB64) {
        byte[] verifier = decodeVerifier(verifierB64);
        return tx.execute(s -> {
            DeviceRow target = ownDevice(userId, deviceId);
            if (!target.state().equals("PENDING")) throw conflict("aparelho-nao-pendente", "Aparelho não está pendente", "Só um aparelho pendente pode ser aprovado.");
            String hash = db.sql("select verifier_hash from messaging_recovery where user_id = :u").param("u", userId).query(String.class).optional()
                    .orElseThrow(() -> forbidden("sem-recuperacao", "Esta conta não tem frase de recuperação."));
            if (!encoder.matches(Base64.getEncoder().encodeToString(verifier), hash)) {
                audit.record(AuditRecord.of("messaging.device_recover").actor(userId, "").resource("messaging_device", deviceId).outcome("denied"));
                throw forbidden("frase-invalida", "A frase de recuperação não confere.");
            }
            db.sql("update messaging_device set state = 'APPROVED', approved_via = 'RECOVERY', approved_at = now() where id = :id").param("id", deviceId).update();
            audit.record(AuditRecord.of("messaging.device_recover").actor(userId, "").resource("messaging_device", deviceId));
            return device(deviceId).orElseThrow();
        });
    }

    public void revokeDevice(UUID userId, UUID deviceId) {
        tx.executeWithoutResult(s -> {
            ownDevice(userId, deviceId);
            db.sql("update messaging_device set state = 'REVOKED', revoked_at = now() where id = :id").param("id", deviceId).update();
            db.sql("delete from messaging_key_package where device_id = :id and consumed_at is null").param("id", deviceId).update();
            audit.record(AuditRecord.of("messaging.device_revoke").actor(userId, "").resource("messaging_device", deviceId));
        });
    }

    public List<DeviceView> myDevices(UUID userId) {
        return db.sql(DEVICE_SELECT + " where d.user_id = :u order by d.created_at").param("u", userId).query(this::deviceView).list();
    }

    /** Diretório para o Authentication Service do cliente: aparelho, dono e chave pública. */
    public DeviceView publicDevice(UUID deviceId) {
        return device(deviceId).orElseThrow(() -> notFound("O aparelho"));
    }

    public int publishKeyPackages(UUID userId, UUID deviceId, List<String> packages) {
        if (packages == null || packages.isEmpty() || packages.size() > 50) throw invalid("keyPackages", "Envie de 1 a 50 KeyPackages.");
        return tx.execute(s -> {
            DeviceRow d = ownDevice(userId, deviceId);
            if (!d.state().equals("APPROVED")) throw forbidden("aparelho-nao-aprovado", "Aprove este aparelho antes de publicar chaves.");
            int free = freeKeyPackages(deviceId);
            if (free + packages.size() > MAX_KEY_PACKAGES) throw invalid("keyPackages", "No máximo " + MAX_KEY_PACKAGES + " KeyPackages livres por aparelho.");
            for (String p : packages) {
                byte[] bytes = decodePayload(p, "keyPackages", 16 * 1024);
                db.sql("insert into messaging_key_package (id, device_id, payload) values (:id, :d, :p)").param("id", UuidV7.generate()).param("d", deviceId)
                        .param("p", bytes).update();
            }
            touch(deviceId);
            return free + packages.size();
        });
    }

    public List<WelcomeView> welcomes(UUID userId, UUID deviceId) {
        DeviceRow d = ownDevice(userId, deviceId);
        if (!d.state().equals("APPROVED")) return List.of();
        touch(deviceId);
        return ciphers.welcomes(deviceId).stream().map(w -> new WelcomeView(w.id(), w.conversationId(), w.epoch(), b64(w.payload()))).toList();
    }

    public void ackWelcome(UUID userId, UUID deviceId, UUID welcomeId) {
        ownDevice(userId, deviceId);
        ciphers.deleteWelcome(deviceId, welcomeId);
    }

    // ---------- conversas ----------

    public List<Map<String, Object>> contacts(UUID userId, String q) {
        String term = q == null ? "" : q.strip();
        if (term.length() < 2) return List.of();
        return db.sql("""
                select u.id, u.display_name, p.crm_uf from user_account u join professional_profile p on p.user_id = u.id
                where u.status = 'ACTIVE' and p.seal_status = 'VERIFIED' and p.visibility <> 'MY_INSTITUTION' and u.id <> :me
                  and lower(u.display_name) like :q order by u.display_name, u.created_at desc limit 10""")
                .param("me", userId).param("q", "%" + term.toLowerCase().replace("%", "").replace("_", "") + "%")
                .query((rs, i) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", rs.getObject("id", UUID.class));
                    m.put("name", rs.getString("display_name"));
                    m.put("uf", rs.getString("crm_uf"));
                    return m;
                }).list();
    }

    public ConversationView create(UUID userId, String kind, List<UUID> memberIds, String title, Integer retentionDays, UUID creatorDeviceId) {
        String k = kind == null ? "" : kind.strip().toUpperCase();
        if (!k.equals("DIRECT") && !k.equals("GROUP")) throw invalid("kind", "Use DIRECT (1:1) ou GROUP.");
        Set<UUID> members = new LinkedHashSet<>(memberIds == null ? List.of() : memberIds);
        members.remove(userId);
        if (k.equals("DIRECT") && members.size() != 1) throw invalid("memberIds", "Conversa 1:1 tem exatamente um contato.");
        if (k.equals("GROUP") && (members.isEmpty() || members.size() + 1 > MAX_GROUP_MEMBERS)) {
            throw invalid("memberIds", "Grupo de 2 a " + MAX_GROUP_MEMBERS + " membros.");
        }
        String t = k.equals("GROUP") ? cleanTitle(title) : null;
        int retention = retention(retentionDays);
        for (UUID m : members) {
            if (!credentialing.isVerified(m)) throw invalid("memberIds", "Só profissionais com selo verificado entram em conversas.");
        }
        String directKey = k.equals("DIRECT") ? directKey(userId, members.iterator().next()) : null;
        return tx.execute(s -> {
            DeviceRow creator = ownDevice(userId, creatorDeviceId);
            if (!creator.state().equals("APPROVED")) throw forbidden("aparelho-nao-aprovado", "Aprove este aparelho antes de conversar.");
            if (directKey != null) {
                Optional<UUID> existing = db.sql("select id from conversation where direct_key = :k").param("k", directKey).query(UUID.class).optional();
                if (existing.isPresent()) {
                    throw new ApiProblemException(HttpStatus.CONFLICT, "conversa-existente", "Conversa já existe", "Abra a conversa que vocês já têm.",
                            Map.of("conversationId", existing.get()));
                }
            }
            UUID id = UuidV7.generate();
            db.sql("insert into conversation (id, kind, title, created_by, direct_key, retention_days) values (:id, :k, :t, :u, :dk, :r)").param("id", id)
                    .param("k", k).param("t", t).param("u", userId).param("dk", directKey).param("r", retention).update();
            db.sql("insert into conversation_member (conversation_id, user_id, role) values (:c, :u, 'OWNER')").param("c", id).param("u", userId).update();
            for (UUID m : members) {
                db.sql("insert into conversation_member (conversation_id, user_id, role) values (:c, :u, 'MEMBER')").param("c", id).param("u", m).update();
            }
            db.sql("insert into conversation_device (conversation_id, device_id, added_epoch) values (:c, :d, 0)").param("c", id).param("d", creatorDeviceId).update();
            audit.record(AuditRecord.of("messaging.conversation_create").actor(userId, "").resource("conversation", id).detail("kind", k)
                    .detail("members", members.size() + 1));
            return view(userId, id);
        });
    }

    public List<Summary> list(UUID userId) {
        List<UUID> ids = db.sql("""
                select c.id from conversation c join conversation_member m on m.conversation_id = c.id
                where m.user_id = :u and m.left_at is null and c.state = 'ACTIVE' order by coalesce(c.last_message_at, c.created_at) desc limit 200""")
                .param("u", userId).query(UUID.class).list();
        List<Summary> out = new ArrayList<>();
        for (UUID id : ids) {
            Map<String, Object> c = db.sql("""
                    select c.kind, c.title, c.last_message_at, c.retention_days, c.last_app_seq > m.last_read_seq as unread
                    from conversation c join conversation_member m on m.conversation_id = c.id and m.user_id = :u where c.id = :c""")
                    .param("u", userId).param("c", id).query().singleRow();
            out.add(new Summary(id, (String) c.get("kind"), (String) c.get("title"), members(id), instant(c.get("last_message_at")), (Boolean) c.get("unread"),
                    ((Number) c.get("retention_days")).intValue()));
        }
        return out;
    }

    public long unreadCount(UUID userId) {
        return db.sql("""
                select count(*) from conversation c join conversation_member m on m.conversation_id = c.id
                where m.user_id = :u and m.left_at is null and c.state = 'ACTIVE' and c.last_app_seq > m.last_read_seq""").param("u", userId)
                .query(Long.class).single();
    }

    public ConversationView get(UUID userId, UUID conversationId) {
        requireMember(userId, conversationId);
        return view(userId, conversationId);
    }

    /** KeyPackages de uso único para os aparelhos que o cliente vai adicionar ao grupo (só os que o servidor espera). */
    public List<ClaimedKeyPackage> claimKeyPackages(UUID userId, UUID conversationId, List<UUID> deviceIds) {
        requireMember(userId, conversationId);
        requireVerified(userId);
        if (deviceIds == null || deviceIds.isEmpty() || deviceIds.size() > MAX_GROUP_MEMBERS * 5) throw invalid("deviceIds", "Informe os aparelhos a adicionar.");
        return tx.execute(s -> {
            Set<UUID> wanted = new LinkedHashSet<>(pendingAdds(conversationId).stream().map(PendingDevice::deviceId).toList());
            List<ClaimedKeyPackage> out = new ArrayList<>();
            for (UUID d : deviceIds) {
                if (!wanted.contains(d)) continue;
                List<Map<String, Object>> rows = db.sql("""
                        select k.id, k.payload, d.user_id from messaging_key_package k join messaging_device d on d.id = k.device_id
                        where k.device_id = :d and k.consumed_at is null order by k.created_at limit 1 for update of k skip locked""")
                        .param("d", d).query().listOfRows();
                if (rows.isEmpty()) continue;
                Map<String, Object> row = rows.get(0);
                db.sql("update messaging_key_package set consumed_at = now() where id = :id").param("id", row.get("id")).update();
                out.add(new ClaimedKeyPackage(d, (UUID) row.get("user_id"), b64((byte[]) row.get("payload"))));
            }
            return out;
        });
    }

    /**
     * Commit MLS: aceito só na época atual (quem chegou atrasado recebe 409, sincroniza e refaz). A época avança,
     * o commit vai para a fila da conversa e o Welcome para cada aparelho adicionado.
     */
    public Map<String, Object> commit(UUID userId, UUID conversationId, CommitInput in) {
        requireMember(userId, conversationId);
        requireVerified(userId);
        if (in == null || in.epoch() == null || in.senderDeviceId() == null) throw invalid("epoch", "Informe época e aparelho.");
        byte[] commit = decodePayload(in.commit(), "commit", MAX_PAYLOAD);
        byte[] welcome = in.welcome() == null || in.welcome().isBlank() ? null : decodePayload(in.welcome(), "welcome", MAX_PAYLOAD);
        List<UUID> added = in.addedDeviceIds() == null ? List.of() : List.copyOf(new LinkedHashSet<>(in.addedDeviceIds()));
        List<UUID> removed = in.removedDeviceIds() == null ? List.of() : List.copyOf(new LinkedHashSet<>(in.removedDeviceIds()));
        if (!added.isEmpty() && welcome == null) throw invalid("welcome", "Adição de aparelhos precisa do Welcome.");
        return tx.execute(s -> {
            Conv c = lockConversation(conversationId);
            requireSenderInGroup(userId, conversationId, in.senderDeviceId());
            if (c.epoch() != in.epoch()) throw staleEpoch(c.epoch());
            Set<UUID> addable = new LinkedHashSet<>(pendingAdds(conversationId).stream().map(PendingDevice::deviceId).toList());
            for (UUID d : added) {
                if (!addable.contains(d)) throw invalid("addedDeviceIds", "Aparelho " + d + " não pode entrar nesta conversa.");
            }
            for (UUID d : removed) {
                if (inGroup(conversationId, d).isEmpty()) throw invalid("removedDeviceIds", "Aparelho " + d + " não está no grupo.");
                if (d.equals(in.senderDeviceId())) throw invalid("removedDeviceIds", "O aparelho não remove a si mesmo por commit.");
            }
            long seq = c.nextSeq();
            ciphers.append(conversationId, new CipherStore.Envelope(seq, UuidV7.generate(), "COMMIT", c.epoch(), in.senderDeviceId(), userId, commit, Instant.now()),
                    Duration.ofDays(c.retentionDays()));
            long newEpoch = c.epoch() + 1;
            for (UUID d : added) {
                db.sql("""
                        insert into conversation_device (conversation_id, device_id, added_epoch) values (:c, :d, :e)
                        on conflict (conversation_id, device_id) do update set added_epoch = :e, removed_epoch = null""")
                        .param("c", conversationId).param("d", d).param("e", newEpoch).update();
                ciphers.putWelcome(d, conversationId, newEpoch, welcome);
            }
            for (UUID d : removed) {
                db.sql("update conversation_device set removed_epoch = :e where conversation_id = :c and device_id = :d").param("e", newEpoch)
                        .param("c", conversationId).param("d", d).update();
            }
            db.sql("update conversation set epoch = :e, next_seq = :n where id = :c").param("e", newEpoch).param("n", seq + 1).param("c", conversationId).update();
            touch(in.senderDeviceId());
            return Map.of("seq", seq, "epoch", newEpoch);
        });
    }

    public Map<String, Object> send(UUID userId, UUID conversationId, MessageInput in) {
        requireMember(userId, conversationId);
        requireVerified(userId);
        if (in == null || in.epoch() == null || in.senderDeviceId() == null) throw invalid("epoch", "Informe época e aparelho.");
        byte[] payload = decodePayload(in.payload(), "payload", MAX_PAYLOAD);
        return tx.execute(s -> {
            Conv c = lockConversation(conversationId);
            requireSenderInGroup(userId, conversationId, in.senderDeviceId());
            if (c.epoch() != in.epoch()) throw staleEpoch(c.epoch());
            long seq = c.nextSeq();
            Instant now = Instant.now();
            ciphers.append(conversationId, new CipherStore.Envelope(seq, UuidV7.generate(), "APP", c.epoch(), in.senderDeviceId(), userId, payload, now),
                    Duration.ofDays(c.retentionDays()));
            db.sql("update conversation set next_seq = :n, last_app_seq = :s, last_message_at = :at where id = :c").param("n", seq + 1).param("s", seq)
                    .param("at", Timestamp.from(now)).param("c", conversationId).update();
            db.sql("update conversation_member set last_read_seq = :s where conversation_id = :c and user_id = :u").param("s", seq).param("c", conversationId)
                    .param("u", userId).update();
            touch(in.senderDeviceId());
            return Map.of("seq", seq, "epoch", c.epoch());
        });
    }

    public List<EnvelopeView> messages(UUID userId, UUID conversationId, long afterSeq, int limit) {
        requireMember(userId, conversationId);
        return ciphers.after(conversationId, Math.max(0, afterSeq), Math.min(Math.max(limit, 1), 500)).stream()
                .map(e -> new EnvelopeView(e.seq(), e.kind(), e.epoch(), e.senderDevice(), e.senderUser(), b64(e.payload()), e.createdAt())).toList();
    }

    public void markRead(UUID userId, UUID conversationId, long seq) {
        requireMember(userId, conversationId);
        db.sql("update conversation_member set last_read_seq = greatest(last_read_seq, least(:s, (select next_seq - 1 from conversation where id = :c))) "
                + "where conversation_id = :c and user_id = :u").param("s", seq).param("c", conversationId).param("u", userId).update();
    }

    public ConversationView setRetention(UUID userId, UUID conversationId, Integer days) {
        requireMember(userId, conversationId);
        int r = retention(days);
        db.sql("update conversation set retention_days = :r where id = :c").param("r", r).param("c", conversationId).update();
        audit.record(AuditRecord.of("messaging.retention").actor(userId, "").resource("conversation", conversationId).detail("days", r));
        return view(userId, conversationId);
    }

    public ConversationView addMembers(UUID userId, UUID conversationId, List<UUID> userIds) {
        Conv c = conv(conversationId);
        if (!c.kind().equals("GROUP")) throw invalid("kind", "Conversa 1:1 não ganha membros: crie um grupo.");
        requireOwner(userId, conversationId);
        if (userIds == null || userIds.isEmpty()) throw invalid("userIds", "Escolha quem entra.");
        for (UUID u : userIds) if (!credentialing.isVerified(u)) throw invalid("userIds", "Só profissionais com selo verificado entram em conversas.");
        return tx.execute(s -> {
            long active = db.sql("select count(*) from conversation_member where conversation_id = :c and left_at is null").param("c", conversationId).query(Long.class).single();
            if (active + userIds.size() > MAX_GROUP_MEMBERS) throw invalid("userIds", "Grupo de até " + MAX_GROUP_MEMBERS + " membros.");
            for (UUID u : userIds) {
                db.sql("""
                        insert into conversation_member (conversation_id, user_id, role) values (:c, :u, 'MEMBER')
                        on conflict (conversation_id, user_id) do update set left_at = null, joined_at = now()""").param("c", conversationId).param("u", u).update();
            }
            audit.record(AuditRecord.of("messaging.member_add").actor(userId, "").resource("conversation", conversationId).detail("count", userIds.size()));
            return view(userId, conversationId);
        });
    }

    /** Sair (o próprio) ou remover (dono do grupo). Os aparelhos dele saem da árvore no próximo commit de quem ficou. */
    public void removeMember(UUID userId, UUID conversationId, UUID memberId) {
        requireMember(userId, conversationId);
        if (!userId.equals(memberId)) requireOwner(userId, conversationId);
        db.sql("update conversation_member set left_at = now() where conversation_id = :c and user_id = :u and left_at is null").param("c", conversationId)
                .param("u", memberId).update();
        audit.record(AuditRecord.of(userId.equals(memberId) ? "messaging.member_leave" : "messaging.member_remove").actor(userId, "")
                .resource("conversation", conversationId));
    }

    // ---------- anexos e denúncias ----------

    public Map<String, Object> attachmentUpload(UUID userId, UUID conversationId, Long size) {
        requireMember(userId, conversationId);
        requireVerified(userId);
        if (size == null || size < 1 || size > MAX_ATTACHMENT) throw invalid("size", "Anexo de até 25 MB.");
        Conv c = conv(conversationId);
        UUID id = UuidV7.generate();
        Instant expires = Instant.now().plus(Duration.ofDays(c.retentionDays()));
        db.sql("insert into conversation_attachment (id, conversation_id, uploader_id, size, expires_at) values (:id, :c, :u, :s, :e)").param("id", id)
                .param("c", conversationId).param("u", userId).param("s", size).param("e", Timestamp.from(expires)).update();
        URI url = objects.presignPut(storage.buckets().chatAttachments(), key(conversationId, id), "application/octet-stream", size, Duration.ofMinutes(15));
        return Map.of("id", id, "uploadUrl", url.toString(), "headers", Map.of("Content-Type", "application/octet-stream"));
    }

    public Map<String, Object> attachmentDownload(UUID userId, UUID conversationId, UUID attachmentId) {
        requireMember(userId, conversationId);
        db.sql("select id from conversation_attachment where id = :id and conversation_id = :c and expires_at > now()").param("id", attachmentId)
                .param("c", conversationId).query(UUID.class).optional().orElseThrow(() -> notFound("O anexo"));
        return Map.of("url", objects.presignGet(storage.buckets().chatAttachments(), key(conversationId, attachmentId), Duration.ofMinutes(5), null).toString());
    }

    /** Denúncia: quem denuncia encaminha, por vontade própria, o trecho já decifrado. É o único texto de chat no servidor. */
    public Map<String, Object> report(UUID userId, UUID conversationId, UUID reportedUserId, String reason, String excerpt) {
        requireMember(userId, conversationId);
        String r = reason == null ? "" : reason.strip();
        String e = excerpt == null ? "" : excerpt.strip();
        List<FieldProblem> problems = new ArrayList<>();
        if (r.length() < 10 || r.length() > 500) problems.add(new FieldProblem("reason", "Explique o motivo em 10 a 500 caracteres."));
        if (e.isEmpty() || e.length() > 8000) problems.add(new FieldProblem("excerpt", "Escolha ao menos uma mensagem (até 8 000 caracteres)."));
        if (!problems.isEmpty()) throw new ValidationProblemException(problems);
        if (reportedUserId != null && !isMember(reportedUserId, conversationId)) throw invalid("reportedUserId", "Essa pessoa não está na conversa.");
        UUID id = UuidV7.generate();
        db.sql("insert into message_report (id, conversation_id, reporter_id, reported_user_id, reason, excerpt) values (:id, :c, :u, :r, :reason, :e)")
                .param("id", id).param("c", conversationId).param("u", userId).param("r", reportedUserId).param("reason", r).param("e", e).update();
        audit.record(AuditRecord.of("messaging.report").actor(userId, "").resource("message_report", id).detail("conversation", conversationId));
        return Map.of("id", id, "state", "OPEN");
    }

    public List<Map<String, Object>> openReports() {
        return db.sql("""
                select r.id, r.conversation_id, r.reason, r.excerpt, r.created_at, a.display_name as reporter, b.display_name as reported, r.reported_user_id
                from message_report r join user_account a on a.id = r.reporter_id left join user_account b on b.id = r.reported_user_id
                where r.state = 'OPEN' order by r.created_at limit 100""").query().listOfRows().stream().map(row -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", row.get("id"));
                    m.put("conversationId", row.get("conversation_id"));
                    m.put("reporter", row.get("reporter"));
                    m.put("reported", row.get("reported"));
                    m.put("reportedUserId", row.get("reported_user_id"));
                    m.put("reason", row.get("reason"));
                    m.put("excerpt", row.get("excerpt"));
                    m.put("createdAt", instant(row.get("created_at")));
                    return m;
                }).toList();
    }

    public void resolveReport(UUID moderatorId, UUID reportId, String resolution) {
        String r = resolution == null ? "" : resolution.strip();
        if (r.length() < 10) throw invalid("resolution", "Registre a decisão em ao menos 10 caracteres.");
        int n = db.sql("update message_report set state = 'RESOLVED', resolved_by = :m, resolved_at = now(), resolution = :r where id = :id and state = 'OPEN'")
                .param("m", moderatorId).param("r", r).param("id", reportId).update();
        if (n == 0) throw notFound("A denúncia");
        audit.record(AuditRecord.of("moderation.message_report_resolve").actor(moderatorId, "").resource("message_report", reportId).justification(r));
    }

    @Scheduled(fixedDelayString = "${doctor-explorer.jobs.chat-attachment-cleanup-ms:3600000}")
    public void deleteExpiredAttachments() {
        List<Map<String, Object>> expired = db.sql("select id, conversation_id from conversation_attachment where expires_at < now() limit 500").query().listOfRows();
        for (Map<String, Object> a : expired) {
            try {
                objects.delete(storage.buckets().chatAttachments(), key((UUID) a.get("conversation_id"), (UUID) a.get("id")));
                db.sql("delete from conversation_attachment where id = :id").param("id", a.get("id")).update();
            } catch (RuntimeException e) {
                // tenta de novo na próxima rodada
            }
        }
    }

    // ---------- internos ----------

    private record DeviceRow(UUID id, UUID userId, byte[] key, String state) {}

    private record Conv(UUID id, String kind, long epoch, long nextSeq, int retentionDays) {}

    private static final String DEVICE_SELECT = """
            select d.id, d.user_id, u.display_name, d.name, d.signature_key, d.state, d.approved_via, d.created_at, d.approved_at, d.last_seen_at,
                   (select count(*) from messaging_key_package k where k.device_id = d.id and k.consumed_at is null) as free
            from messaging_device d join user_account u on u.id = d.user_id""";

    private DeviceView deviceView(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
        return new DeviceView(rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class), rs.getString("display_name"), rs.getString("name"),
                b64(rs.getBytes("signature_key")), rs.getString("state"), rs.getString("approved_via"), ts(rs.getTimestamp("created_at")),
                ts(rs.getTimestamp("approved_at")), ts(rs.getTimestamp("last_seen_at")), rs.getInt("free"));
    }

    private Optional<DeviceView> device(UUID id) {
        return db.sql(DEVICE_SELECT + " where d.id = :id").param("id", id).query(this::deviceView).optional();
    }

    private DeviceRow ownDevice(UUID userId, UUID deviceId) {
        if (deviceId == null) throw invalid("deviceId", "Informe o aparelho.");
        return db.sql("select id, user_id, signature_key, state from messaging_device where id = :id and user_id = :u").param("id", deviceId).param("u", userId)
                .query((rs, i) -> new DeviceRow(rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class), rs.getBytes("signature_key"), rs.getString("state")))
                .optional().orElseThrow(() -> notFound("O aparelho"));
    }

    private int freeKeyPackages(UUID deviceId) {
        return db.sql("select count(*) from messaging_key_package where device_id = :d and consumed_at is null").param("d", deviceId).query(Long.class).single().intValue();
    }

    private void touch(UUID deviceId) {
        db.sql("update messaging_device set last_seen_at = now() where id = :d").param("d", deviceId).update();
    }

    private Conv conv(UUID id) {
        return db.sql("select id, kind, epoch, next_seq, retention_days from conversation where id = :id").param("id", id).query(Conv.class).optional()
                .orElseThrow(() -> notFound("A conversa"));
    }

    private Conv lockConversation(UUID id) {
        return db.sql("select id, kind, epoch, next_seq, retention_days from conversation where id = :id for update").param("id", id).query(Conv.class).optional()
                .orElseThrow(() -> notFound("A conversa"));
    }

    private boolean isMember(UUID userId, UUID conversationId) {
        return db.sql("select count(*) from conversation_member where conversation_id = :c and user_id = :u and left_at is null").param("c", conversationId)
                .param("u", userId).query(Long.class).single() > 0;
    }

    private void requireMember(UUID userId, UUID conversationId) {
        if (!isMember(userId, conversationId)) throw notFound("A conversa");
    }

    private void requireOwner(UUID userId, UUID conversationId) {
        String role = db.sql("select role from conversation_member where conversation_id = :c and user_id = :u and left_at is null").param("c", conversationId)
                .param("u", userId).query(String.class).optional().orElse("");
        if (!role.equals("OWNER")) throw forbidden("nao-dono", "Só quem criou o grupo gerencia os membros.");
    }

    private void requireVerified(UUID userId) {
        if (!credentialing.isVerified(userId)) throw forbidden("selo-necessario", "Só profissionais com selo verificado enviam mensagens.");
    }

    private Optional<Long> inGroup(UUID conversationId, UUID deviceId) {
        return db.sql("select added_epoch from conversation_device where conversation_id = :c and device_id = :d and removed_epoch is null")
                .param("c", conversationId).param("d", deviceId).query(Long.class).optional();
    }

    private void requireSenderInGroup(UUID userId, UUID conversationId, UUID deviceId) {
        DeviceRow d = ownDevice(userId, deviceId);
        if (!d.state().equals("APPROVED")) throw forbidden("aparelho-nao-aprovado", "Este aparelho não está aprovado.");
        if (inGroup(conversationId, deviceId).isEmpty()) {
            throw new ApiProblemException(HttpStatus.CONFLICT, "aparelho-fora-do-grupo", "Aparelho fora da conversa",
                    "Este aparelho ainda não entrou no grupo. Aguarde outro membro adicioná-lo.");
        }
    }

    /** Aparelhos aprovados de membros ativos e verificados que ainda não estão na árvore. */
    private List<PendingDevice> pendingAdds(UUID conversationId) {
        return db.sql("""
                select d.id, d.user_id, exists(select 1 from messaging_key_package k where k.device_id = d.id and k.consumed_at is null) as has_kp
                from conversation_member m join messaging_device d on d.user_id = m.user_id and d.state = 'APPROVED'
                join professional_profile p on p.user_id = m.user_id and p.seal_status = 'VERIFIED'
                where m.conversation_id = :c and m.left_at is null
                  and not exists (select 1 from conversation_device cd where cd.conversation_id = :c and cd.device_id = d.id and cd.removed_epoch is null)
                order by d.created_at""").param("c", conversationId)
                .query((rs, i) -> new PendingDevice(rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class), rs.getBoolean("has_kp"))).list();
    }

    /** Aparelhos ainda na árvore que precisam sair: revogados, de quem saiu ou de quem perdeu o selo. */
    private List<UUID> pendingRemovals(UUID conversationId) {
        return db.sql("""
                select cd.device_id from conversation_device cd join messaging_device d on d.id = cd.device_id
                left join conversation_member m on m.conversation_id = cd.conversation_id and m.user_id = d.user_id
                left join professional_profile p on p.user_id = d.user_id
                where cd.conversation_id = :c and cd.removed_epoch is null
                  and (d.state <> 'APPROVED' or m.user_id is null or m.left_at is not null or p.seal_status is distinct from 'VERIFIED')""")
                .param("c", conversationId).query(UUID.class).list();
    }

    private List<MemberView> members(UUID conversationId) {
        return db.sql("""
                select m.user_id, u.display_name, m.role, coalesce(p.seal_status = 'VERIFIED', false) as verified, m.joined_at
                from conversation_member m join user_account u on u.id = m.user_id left join professional_profile p on p.user_id = m.user_id
                where m.conversation_id = :c and m.left_at is null order by m.joined_at""").param("c", conversationId)
                .query((rs, i) -> new MemberView(rs.getObject("user_id", UUID.class), rs.getString("display_name"), rs.getString("role"), rs.getBoolean("verified"),
                        ts(rs.getTimestamp("joined_at")))).list();
    }

    private ConversationView view(UUID userId, UUID conversationId) {
        Map<String, Object> c = db.sql("""
                select c.kind, c.title, c.epoch, c.retention_days, c.state, c.created_at, c.last_message_at, c.last_app_seq > m.last_read_seq as unread, m.last_read_seq
                from conversation c join conversation_member m on m.conversation_id = c.id and m.user_id = :u where c.id = :c""")
                .param("u", userId).param("c", conversationId).query().singleRow();
        List<UUID> devices = db.sql("select device_id from conversation_device where conversation_id = :c and removed_epoch is null").param("c", conversationId)
                .query(UUID.class).list();
        return new ConversationView(conversationId, (String) c.get("kind"), (String) c.get("title"), ((Number) c.get("epoch")).longValue(),
                ((Number) c.get("retention_days")).intValue(), (String) c.get("state"), instant(c.get("created_at")), instant(c.get("last_message_at")),
                (Boolean) c.get("unread"), ((Number) c.get("last_read_seq")).longValue(), members(conversationId), devices, pendingAdds(conversationId),
                pendingRemovals(conversationId));
    }

    private String cleanTitle(String title) {
        String t = title == null ? "" : title.strip();
        if (t.length() < 3 || t.length() > 80) throw invalid("title", "Dê um nome de 3 a 80 caracteres ao grupo.");
        if (!detector.scan(t).isEmpty()) {
            throw invalid("title", "O nome do grupo não é cifrado: tire dele qualquer dado que identifique paciente.");
        }
        return t;
    }

    private static int retention(Integer days) {
        int r = days == null ? 90 : days;
        if (!RETENTIONS.contains(r)) throw invalid("retentionDays", "Retenção de 1, 7 ou 90 dias.");
        return r;
    }

    static String directKey(UUID a, UUID b) {
        return a.compareTo(b) < 0 ? a + ":" + b : b + ":" + a;
    }

    private static String key(UUID conversationId, UUID attachmentId) {
        return "chat/" + conversationId + "/" + attachmentId;
    }

    static boolean verifyEd25519(byte[] publicKey, byte[] message, String signatureB64) {
        try {
            byte[] sig = Base64.getDecoder().decode(signatureB64 == null ? "" : signatureB64);
            if (sig.length != 64) return false;
            Ed25519Signer v = new Ed25519Signer();
            v.init(false, new Ed25519PublicKeyParameters(publicKey, 0));
            v.update(message, 0, message.length);
            return v.verifySignature(sig);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static byte[] decodeKey(String b64, String field) {
        try {
            byte[] k = Base64.getDecoder().decode(b64 == null ? "" : b64);
            if (k.length != 32) throw invalid(field, "Chave Ed25519 de 32 bytes em Base64.");
            return k;
        } catch (IllegalArgumentException e) {
            throw invalid(field, "Chave Ed25519 de 32 bytes em Base64.");
        }
    }

    private static byte[] decodeVerifier(String b64) {
        try {
            byte[] v = Base64.getDecoder().decode(b64 == null ? "" : b64);
            if (v.length != 32) throw invalid("recoveryVerifier", "Gere a frase de recuperação neste aparelho antes de continuar.");
            return v;
        } catch (IllegalArgumentException e) {
            throw invalid("recoveryVerifier", "Gere a frase de recuperação neste aparelho antes de continuar.");
        }
    }

    private static byte[] decodePayload(String b64, String field, int max) {
        try {
            byte[] p = Base64.getDecoder().decode(b64 == null ? "" : b64);
            if (p.length == 0 || p.length > max) throw invalid(field, "Conteúdo vazio ou grande demais.");
            return p;
        } catch (IllegalArgumentException e) {
            throw invalid(field, "Conteúdo em Base64.");
        }
    }

    private static String b64(byte[] b) {
        return Base64.getEncoder().encodeToString(b);
    }

    private static Instant ts(Timestamp t) {
        return t == null ? null : t.toInstant();
    }

    private static Instant instant(Object o) {
        if (o == null) return null;
        if (o instanceof Timestamp t) return t.toInstant();
        if (o instanceof java.time.OffsetDateTime odt) return odt.toInstant();
        if (o instanceof Instant i) return i;
        throw new IllegalStateException("data inesperada: " + o.getClass());
    }

    private static ApiProblemException staleEpoch(long current) {
        return new ApiProblemException(HttpStatus.CONFLICT, "epoca-desatualizada", "Conversa mudou", "Sincronize a conversa e tente de novo.",
                Map.of("epoch", current));
    }

    private static ValidationProblemException invalid(String field, String message) {
        return new ValidationProblemException(List.of(new FieldProblem(field, message)));
    }

    private static ApiProblemException forbidden(String type, String detail) {
        return new ApiProblemException(HttpStatus.FORBIDDEN, type, "Não permitido", detail);
    }

    private static ApiProblemException conflict(String type, String title, String detail) {
        return new ApiProblemException(HttpStatus.CONFLICT, type, title, detail);
    }

    private static ApiProblemException notFound(String what) {
        return new ApiProblemException(HttpStatus.NOT_FOUND, "nao-encontrado", "Não encontrado", what + " não existe ou você não tem acesso.");
    }
}
