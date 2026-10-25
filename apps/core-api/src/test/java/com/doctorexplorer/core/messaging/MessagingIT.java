package com.doctorexplorer.core.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.datastax.oss.driver.api.core.CqlSession;
import com.doctorexplorer.core.audit.CassandraSchema;
import com.doctorexplorer.core.identity.service.LoggingMailer;
import com.doctorexplorer.core.support.ApiClient;
import com.doctorexplorer.core.support.TestCrm;
import com.doctorexplorer.core.support.TestInfra;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator;
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * RF06 · Delivery Service com PostgreSQL, Cassandra e S3 reais. O teste faz o papel dos clientes MLS: as chaves de
 * aparelho são Ed25519 de verdade (aprovação assinada), e KeyPackages, commits e mensagens são bytes opacos, como o
 * servidor os enxerga. O fluxo MLS em si (ts-mls) é coberto no web (vitest) e no e2e.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Import(TestInfra.class)
class MessagingIT {

    static final String PASSWORD = "frase longa e segura 2026";
    static final ObjectMapper JSON = new ObjectMapper();
    static final SecureRandom RANDOM = new SecureRandom();

    @Autowired MockMvc mvc;
    @Autowired LoggingMailer mailer;
    @Autowired StringRedisTemplate redis;
    @Autowired JdbcTemplate jdbc;
    @Autowired CqlSession cql;
    @Autowired CassandraSchema schema;
    @Autowired MessagingService messaging;

    private final HttpClient http = HttpClient.newHttpClient();

    record Device(UUID id, Ed25519PrivateKeyParameters key) {
        String publicKey() {
            return Base64.getEncoder().encodeToString(key.generatePublicKey().getEncoded());
        }

        String sign(String message) {
            Ed25519Signer s = new Ed25519Signer();
            s.init(true, key);
            byte[] m = message.getBytes(StandardCharsets.UTF_8);
            s.update(m, 0, m.length);
            return Base64.getEncoder().encodeToString(s.generateSignature());
        }
    }

    @BeforeEach
    void setUp() {
        mailer.clear();
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    private ApiClient doctor(boolean verified) throws Exception {
        ApiClient c = new ApiClient(mvc);
        c.register("chat+" + System.nanoTime() + "@example.org", PASSWORD);
        c.activateEmail(mailer);
        if (verified) c.post("/api/v1/credentials", "{\"crmNumber\":\"" + TestCrm.next("12") + "\",\"crmUf\":\"PR\",\"specialties\":[]}");
        return c;
    }

    private static UUID me(ApiClient c) throws Exception {
        return UUID.fromString(json(c.get("/api/v1/me")).get("id").asString());
    }

    private static JsonNode json(MockHttpServletResponse r) throws Exception {
        return JSON.readTree(r.getContentAsString());
    }

    private static Ed25519PrivateKeyParameters newKey() {
        Ed25519KeyPairGenerator g = new Ed25519KeyPairGenerator();
        g.init(new Ed25519KeyGenerationParameters(RANDOM));
        AsymmetricCipherKeyPair kp = g.generateKeyPair();
        return (Ed25519PrivateKeyParameters) kp.getPrivate();
    }

    private static String b64(String s) {
        return Base64.getEncoder().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String verifier(String seed) {
        byte[] v = new byte[32];
        byte[] s = seed.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(s, 0, v, 0, Math.min(32, s.length));
        return Base64.getEncoder().encodeToString(v);
    }

    private Device register(ApiClient c, String name, String recovery, String expectedState) throws Exception {
        Ed25519PrivateKeyParameters k = newKey();
        String body = "{\"name\":\"" + name + "\",\"signatureKey\":\"" + Base64.getEncoder().encodeToString(k.generatePublicKey().getEncoded()) + "\""
                + (recovery == null ? "" : ",\"recoveryVerifier\":\"" + recovery + "\"") + "}";
        MockHttpServletResponse r = c.post("/api/v1/messaging/devices", body);
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(201);
        JsonNode d = json(r);
        assertThat(d.get("state").asString()).isEqualTo(expectedState);
        return new Device(UUID.fromString(d.get("id").asString()), k);
    }

    private Device readyDevice(ApiClient c, String name, int keyPackages, String tag) throws Exception {
        Device d = register(c, name, verifier("rec-" + tag), "APPROVED");
        if (keyPackages > 0) {
            StringBuilder kps = new StringBuilder();
            for (int i = 0; i < keyPackages; i++) kps.append(i == 0 ? "" : ",").append('"').append(b64("kp-" + tag + "-" + i)).append('"');
            assertThat(c.post("/api/v1/messaging/devices/" + d.id() + "/key-packages", "{\"keyPackages\":[" + kps + "]}").getStatus()).isEqualTo(200);
        }
        return d;
    }

    @Test
    void devicesNeedRecoveryOnFirstUseAndSignedApprovalOrThePhraseLater() throws Exception {
        ApiClient ana = doctor(true);
        Ed25519PrivateKeyParameters k = newKey();
        MockHttpServletResponse noPhrase = ana.post("/api/v1/messaging/devices",
                "{\"name\":\"Notebook\",\"signatureKey\":\"" + Base64.getEncoder().encodeToString(k.generatePublicKey().getEncoded()) + "\"}");
        assertThat(noPhrase.getStatus()).isEqualTo(422);
        assertThat(noPhrase.getContentAsString()).contains("recoveryVerifier");

        Device first = register(ana, "Notebook", verifier("minha frase"), "APPROVED");
        Device second = register(ana, "Celular", null, "PENDING");
        assertThat(ana.post("/api/v1/messaging/devices/" + second.id() + "/key-packages", "{\"keyPackages\":[\"" + b64("x") + "\"]}").getStatus())
                .as("pendente não publica chave").isEqualTo(403);

        String message = MessagingService.APPROVAL_CONTEXT + second.id() + "|" + second.publicKey();
        MockHttpServletResponse forged = ana.post("/api/v1/messaging/devices/" + second.id() + "/approve",
                "{\"approverDeviceId\":\"" + first.id() + "\",\"signature\":\"" + second.sign(message) + "\"}");
        assertThat(forged.getStatus()).as("o pendente não se aprova sozinho").isEqualTo(403);
        assertThat(ana.post("/api/v1/messaging/devices/" + second.id() + "/approve",
                "{\"approverDeviceId\":\"" + second.id() + "\",\"signature\":\"" + second.sign(message) + "\"}").getStatus()).isEqualTo(403);
        MockHttpServletResponse ok = ana.post("/api/v1/messaging/devices/" + second.id() + "/approve",
                "{\"approverDeviceId\":\"" + first.id() + "\",\"signature\":\"" + first.sign(message) + "\"}");
        assertThat(ok.getStatus()).as(ok.getContentAsString()).isEqualTo(200);
        assertThat(json(ok).get("approvedVia").asString()).isEqualTo("DEVICE");

        Device third = register(ana, "Tablet", null, "PENDING");
        assertThat(ana.post("/api/v1/messaging/devices/" + third.id() + "/recover", "{\"verifier\":\"" + verifier("outra frase") + "\"}").getStatus()).isEqualTo(403);
        MockHttpServletResponse recovered = ana.post("/api/v1/messaging/devices/" + third.id() + "/recover", "{\"verifier\":\"" + verifier("minha frase") + "\"}");
        assertThat(json(recovered).get("approvedVia").asString()).isEqualTo("RECOVERY");

        // a chave pública é única e outro médico não aprova nem enxerga como seu
        ApiClient bruno = doctor(true);
        assertThat(bruno.post("/api/v1/messaging/devices/" + third.id() + "/recover", "{\"verifier\":\"" + verifier("minha frase") + "\"}").getStatus())
                .isEqualTo(404);
        assertThat(json(bruno.get("/api/v1/messaging/devices/" + first.id() + "/public")).get("signatureKey").asString()).isEqualTo(first.publicKey());
        assertThat(ana.delete("/api/v1/messaging/devices/" + third.id()).getStatus()).isEqualTo(204);
        assertThat(json(ana.get("/api/v1/messaging/devices")).toString()).contains("\"REVOKED\"");

        // sem selo não registra aparelho
        ApiClient carla = doctor(false);
        assertThat(carla.post("/api/v1/messaging/devices", "{\"name\":\"x\",\"signatureKey\":\"" + first.publicKey() + "\"}").getStatus()).isEqualTo(403);
    }

    @Test
    void deliveryServiceOrdersCommitsByEpochAndStoresOnlyOpaqueBytesWithTtl() throws Exception {
        ApiClient ana = doctor(true);
        ApiClient bruno = doctor(true);
        UUID brunoId = me(bruno);
        Device anaDev = readyDevice(ana, "Notebook", 0, "ana" + System.nanoTime());
        Device brunoDev = readyDevice(bruno, "Celular", 2, "bruno" + System.nanoTime());

        assertThat(ana.post("/api/v1/messaging/conversations", "{\"kind\":\"DIRECT\",\"memberIds\":[\"" + me(doctor(false)) + "\"],\"deviceId\":\"" + anaDev.id() + "\"}")
                .getStatus()).as("contato sem selo").isEqualTo(422);
        MockHttpServletResponse created = ana.post("/api/v1/messaging/conversations",
                "{\"kind\":\"DIRECT\",\"memberIds\":[\"" + brunoId + "\"],\"deviceId\":\"" + anaDev.id() + "\"}");
        assertThat(created.getStatus()).as(created.getContentAsString()).isEqualTo(201);
        JsonNode conv = json(created);
        String cid = conv.get("id").asString();
        assertThat(conv.get("epoch").asLong()).isZero();
        assertThat(conv.get("retentionDays").asInt()).isEqualTo(90);
        assertThat(conv.get("devicesToAdd").toString()).contains(brunoDev.id().toString()).contains("\"hasKeyPackage\":true");
        MockHttpServletResponse dup = ana.post("/api/v1/messaging/conversations", "{\"kind\":\"DIRECT\",\"memberIds\":[\"" + brunoId + "\"],\"deviceId\":\"" + anaDev.id() + "\"}");
        assertThat(dup.getStatus()).isEqualTo(409);
        assertThat(dup.getContentAsString()).contains(cid);

        // KeyPackage de uso único: cada pedido consome um
        String kp1 = json(ana.post("/api/v1/messaging/conversations/" + cid + "/key-packages", "{\"deviceIds\":[\"" + brunoDev.id() + "\"]}")).get("items").get(0)
                .get("keyPackage").asString();
        String kp2 = json(ana.post("/api/v1/messaging/conversations/" + cid + "/key-packages", "{\"deviceIds\":[\"" + brunoDev.id() + "\"]}")).get("items").get(0)
                .get("keyPackage").asString();
        assertThat(kp1).isNotEqualTo(kp2);
        assertThat(json(ana.post("/api/v1/messaging/conversations/" + cid + "/key-packages", "{\"deviceIds\":[\"" + brunoDev.id() + "\"]}")).get("items")).isEmpty();
        assertThat(ana.post("/api/v1/messaging/conversations/" + cid + "/key-packages", "{\"deviceIds\":[\"" + anaDev.id() + "\"]}").getContentAsString())
                .as("quem já está no grupo não tem KeyPackage para pegar").contains("\"items\":[]");

        // commit fora da época é recusado; na época certa avança e entrega o Welcome ao aparelho adicionado
        String commitBody = "{\"epoch\":%d,\"senderDeviceId\":\"" + anaDev.id() + "\",\"commit\":\"" + b64("commit-add") + "\",\"welcome\":\"" + b64("welcome-bruno")
                + "\",\"addedDeviceIds\":[\"" + brunoDev.id() + "\"]}";
        MockHttpServletResponse stale = ana.post("/api/v1/messaging/conversations/" + cid + "/commits", commitBody.formatted(3));
        assertThat(stale.getStatus()).isEqualTo(409);
        assertThat(stale.getContentAsString()).contains("epoca-desatualizada");
        MockHttpServletResponse committed = ana.post("/api/v1/messaging/conversations/" + cid + "/commits", commitBody.formatted(0));
        assertThat(committed.getStatus()).as(committed.getContentAsString()).isEqualTo(200);
        assertThat(json(committed).get("epoch").asLong()).isEqualTo(1);
        assertThat(ana.post("/api/v1/messaging/conversations/" + cid + "/commits", commitBody.formatted(1)).getStatus())
                .as("o mesmo aparelho não entra duas vezes").isEqualTo(422);
        JsonNode welcomes = json(bruno.get("/api/v1/messaging/devices/" + brunoDev.id() + "/welcomes")).get("items");
        assertThat(welcomes).hasSize(1);
        assertThat(welcomes.get(0).get("payload").asString()).isEqualTo(b64("welcome-bruno"));
        assertThat(welcomes.get(0).get("epoch").asLong()).isEqualTo(1);
        assertThat(ana.get("/api/v1/messaging/devices/" + brunoDev.id() + "/welcomes").getStatus()).as("Welcome é só do dono do aparelho").isEqualTo(404);
        assertThat(bruno.delete("/api/v1/messaging/devices/" + brunoDev.id() + "/welcomes/" + welcomes.get(0).get("id").asString()).getStatus()).isEqualTo(204);
        assertThat(json(bruno.get("/api/v1/messaging/devices/" + brunoDev.id() + "/welcomes")).get("items")).isEmpty();

        // mensagem: só na época atual, só de aparelho no grupo, e o servidor guarda exatamente os bytes recebidos
        String secret = "texto que o servidor nunca deveria ver";
        String sendBody = "{\"epoch\":%d,\"senderDeviceId\":\"" + brunoDev.id() + "\",\"payload\":\"" + b64(secret) + "\"}";
        assertThat(bruno.post("/api/v1/messaging/conversations/" + cid + "/messages", sendBody.formatted(0)).getStatus()).isEqualTo(409);
        MockHttpServletResponse sent = bruno.post("/api/v1/messaging/conversations/" + cid + "/messages", sendBody.formatted(1));
        assertThat(sent.getStatus()).as(sent.getContentAsString()).isEqualTo(200);
        long seq = json(sent).get("seq").asLong();
        assertThat(json(ana.get("/api/v1/messaging/unread")).get("conversations").asLong()).isEqualTo(1);
        JsonNode items = json(ana.get("/api/v1/messaging/conversations/" + cid + "/messages?after=0")).get("items");
        assertThat(items).hasSize(2);
        assertThat(items.get(0).get("kind").asString()).isEqualTo("COMMIT");
        assertThat(items.get(1).get("kind").asString()).isEqualTo("APP");
        assertThat(items.get(1).get("payload").asString()).isEqualTo(b64(secret));
        assertThat(items.get(1).get("senderUserId").asString()).isEqualTo(brunoId.toString());
        assertThat(json(ana.get("/api/v1/messaging/conversations/" + cid + "/messages?after=" + seq)).get("items")).isEmpty();
        var row = cql.execute("SELECT payload, TTL(payload) AS ttl FROM " + schema.keyspace() + ".mls_message WHERE conversation_id = " + cid + " AND seq = " + seq).one();
        assertThat(row.getInt("ttl")).isBetween(89 * 86400, 90 * 86400);
        Integer plainInPg = jdbc.queryForObject("select count(*) from conversation where title like ?", Integer.class, "%" + secret + "%");
        assertThat(plainInPg).isZero();
        assertThat(ana.post("/api/v1/messaging/conversations/" + cid + "/read", "{\"seq\":" + seq + "}").getStatus()).isEqualTo(204);
        assertThat(json(ana.get("/api/v1/messaging/unread")).get("conversations").asLong()).isZero();

        // retenção curta vale para as próximas mensagens
        assertThat(json(ana.patch("/api/v1/messaging/conversations/" + cid, "{\"retentionDays\":1}")).get("retentionDays").asInt()).isEqualTo(1);
        long seq2 = json(bruno.post("/api/v1/messaging/conversations/" + cid + "/messages", sendBody.formatted(1))).get("seq").asLong();
        var row2 = cql.execute("SELECT TTL(payload) AS ttl FROM " + schema.keyspace() + ".mls_message WHERE conversation_id = " + cid + " AND seq = " + seq2).one();
        assertThat(row2.getInt("ttl")).isBetween(86000, 86400);
        assertThat(ana.patch("/api/v1/messaging/conversations/" + cid, "{\"retentionDays\":30}").getStatus()).isEqualTo(422);

        // quem não é membro não vê nada
        ApiClient carla = doctor(true);
        assertThat(carla.get("/api/v1/messaging/conversations/" + cid + "/messages").getStatus()).isEqualTo(404);
        assertThat(carla.get("/api/v1/messaging/conversations/" + cid).getStatus()).isEqualTo(404);

        // aparelho revogado: sai da árvore no próximo commit e não envia mais
        assertThat(bruno.delete("/api/v1/messaging/devices/" + brunoDev.id()).getStatus()).isEqualTo(204);
        assertThat(json(ana.get("/api/v1/messaging/conversations/" + cid)).get("devicesToRemove").toString()).contains(brunoDev.id().toString());
        assertThat(bruno.post("/api/v1/messaging/conversations/" + cid + "/messages", sendBody.formatted(1)).getStatus()).isEqualTo(403);
        MockHttpServletResponse removal = ana.post("/api/v1/messaging/conversations/" + cid + "/commits",
                "{\"epoch\":1,\"senderDeviceId\":\"" + anaDev.id() + "\",\"commit\":\"" + b64("commit-remove") + "\",\"removedDeviceIds\":[\"" + brunoDev.id() + "\"]}");
        assertThat(removal.getStatus()).as(removal.getContentAsString()).isEqualTo(200);
        assertThat(json(ana.get("/api/v1/messaging/conversations/" + cid)).get("devicesToRemove")).isEmpty();
    }

    @Test
    void groupsAttachmentsAndReports() throws Exception {
        ApiClient ana = doctor(true);
        ApiClient bruno = doctor(true);
        UUID brunoId = me(bruno);
        Device anaDev = readyDevice(ana, "Notebook", 0, "g" + System.nanoTime());

        MockHttpServletResponse leaky = ana.post("/api/v1/messaging/conversations",
                "{\"kind\":\"GROUP\",\"title\":\"Caso CPF 529.982.247-25\",\"memberIds\":[\"" + brunoId + "\"],\"deviceId\":\"" + anaDev.id() + "\"}");
        assertThat(leaky.getStatus()).isEqualTo(422);
        assertThat(leaky.getContentAsString()).contains("não é cifrado");
        String cid = json(ana.post("/api/v1/messaging/conversations",
                "{\"kind\":\"GROUP\",\"title\":\"Plantão UTI adulto\",\"retentionDays\":7,\"memberIds\":[\"" + brunoId + "\"],\"deviceId\":\"" + anaDev.id() + "\"}"))
                .get("id").asString();
        assertThat(json(bruno.get("/api/v1/messaging/conversations")).toString()).contains("Plantão UTI adulto");

        ApiClient carla = doctor(true);
        UUID carlaId = me(carla);
        assertThat(bruno.post("/api/v1/messaging/conversations/" + cid + "/members", "{\"userIds\":[\"" + carlaId + "\"]}").getStatus()).as("só o dono").isEqualTo(403);
        assertThat(json(ana.post("/api/v1/messaging/conversations/" + cid + "/members", "{\"userIds\":[\"" + carlaId + "\"]}")).get("members")).hasSize(3);
        assertThat(carla.delete("/api/v1/messaging/conversations/" + cid + "/members/" + carlaId).getStatus()).as("sair").isEqualTo(204);
        assertThat(carla.get("/api/v1/messaging/conversations/" + cid).getStatus()).isEqualTo(404);

        // anexo: o navegador envia o arquivo já cifrado direto ao S3
        JsonNode up = json(ana.post("/api/v1/messaging/conversations/" + cid + "/attachments", "{\"size\":16}"));
        byte[] cipher = new byte[16];
        RANDOM.nextBytes(cipher);
        HttpResponse<String> put = http.send(HttpRequest.newBuilder(URI.create(up.get("uploadUrl").asString())).header("Content-Type", "application/octet-stream")
                .PUT(HttpRequest.BodyPublishers.ofByteArray(cipher)).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(put.statusCode()).as(put.body()).isEqualTo(200);
        String url = json(bruno.get("/api/v1/messaging/conversations/" + cid + "/attachments/" + up.get("id").asString())).get("url").asString();
        assertThat(http.send(HttpRequest.newBuilder(URI.create(url)).build(), HttpResponse.BodyHandlers.ofByteArray()).body()).isEqualTo(cipher);
        assertThat(carla.get("/api/v1/messaging/conversations/" + cid + "/attachments/" + up.get("id").asString()).getStatus()).isEqualTo(404);
        assertThat(ana.post("/api/v1/messaging/conversations/" + cid + "/attachments", "{\"size\":" + (MessagingService.MAX_ATTACHMENT + 1) + "}").getStatus())
                .isEqualTo(422);

        // denúncia: o trecho decifrado só chega ao servidor porque quem denuncia o encaminhou
        assertThat(bruno.post("/api/v1/messaging/conversations/" + cid + "/reports", "{\"reason\":\"curto\",\"excerpt\":\"x\"}").getStatus()).isEqualTo(422);
        MockHttpServletResponse report = bruno.post("/api/v1/messaging/conversations/" + cid + "/reports",
                "{\"reportedUserId\":\"" + me(ana) + "\",\"reason\":\"Mensagem ofensiva a colega\",\"excerpt\":\"[10:02] Dra. Ana: trecho encaminhado\"}");
        assertThat(report.getStatus()).as(report.getContentAsString()).isEqualTo(201);
        assertThat(messaging.openReports()).anySatisfy(r -> assertThat(r.get("excerpt")).isEqualTo("[10:02] Dra. Ana: trecho encaminhado"));
        assertThat(bruno.get("/api/v1/admin/moderation/message-reports").getStatus()).as("fila só para moderação").isIn(401, 403);
    }
}
