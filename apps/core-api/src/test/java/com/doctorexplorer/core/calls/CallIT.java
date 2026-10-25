package com.doctorexplorer.core.calls;

import static org.assertj.core.api.Assertions.assertThat;

import com.doctorexplorer.core.identity.service.LoggingMailer;
import com.doctorexplorer.core.internal.InternalTokens;
import com.doctorexplorer.core.support.ApiClient;
import com.doctorexplorer.core.support.TestCrm;
import com.doctorexplorer.core.support.TestInfra;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.bouncycastle.crypto.generators.Ed25519KeyPairGenerator;
import org.bouncycastle.crypto.params.Ed25519KeyGenerationParameters;
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
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
 * RF07 · convite, admissão no SFU e metadados das chamadas com PostgreSQL real. A mídia (mediasoup + E2EE por quadro)
 * é coberta em apps/rtc e no e2e com dois navegadores.
 */
@SpringBootTest(properties = {"doctor-explorer.rtc.turn-urls=turn:turn.example.org:3478?transport=udp", "doctor-explorer.rtc.turn-secret=segredo-de-teste-do-coturn"})
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Import(TestInfra.class)
class CallIT {

    static final String PASSWORD = "frase longa e segura 2026";
    static final ObjectMapper JSON = new ObjectMapper();

    @Autowired MockMvc mvc;
    @Autowired LoggingMailer mailer;
    @Autowired StringRedisTemplate redis;
    @Autowired JdbcTemplate jdbc;
    @Autowired InternalTokens tokens;
    @Autowired CallService calls;

    record Doc(ApiClient client, UUID id, UUID device) {}

    @BeforeEach
    void setUp() {
        mailer.clear();
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    private static JsonNode json(MockHttpServletResponse r) throws Exception {
        return JSON.readTree(r.getContentAsString());
    }

    private Doc doctor() throws Exception {
        ApiClient c = new ApiClient(mvc);
        c.register("call+" + System.nanoTime() + "@example.org", PASSWORD);
        c.activateEmail(mailer);
        c.post("/api/v1/credentials", "{\"crmNumber\":\"" + TestCrm.next("12") + "\",\"crmUf\":\"PR\",\"specialties\":[]}");
        UUID id = UUID.fromString(json(c.get("/api/v1/me")).get("id").asString());
        Ed25519KeyPairGenerator g = new Ed25519KeyPairGenerator();
        g.init(new Ed25519KeyGenerationParameters(new SecureRandom()));
        byte[] pub = ((Ed25519PrivateKeyParameters) g.generateKeyPair().getPrivate()).generatePublicKey().getEncoded();
        MockHttpServletResponse d = c.post("/api/v1/messaging/devices", "{\"name\":\"Notebook\",\"signatureKey\":\"" + Base64.getEncoder().encodeToString(pub)
                + "\",\"recoveryVerifier\":\"" + Base64.getEncoder().encodeToString(new byte[32]) + "\"}");
        return new Doc(c, id, UUID.fromString(json(d).get("id").asString()));
    }

    private String conversation(Doc owner, Doc... others) throws Exception {
        StringBuilder ids = new StringBuilder();
        for (Doc o : others) ids.append(ids.isEmpty() ? "" : ",").append('"').append(o.id()).append('"');
        String body = others.length == 1 ? "{\"kind\":\"DIRECT\",\"memberIds\":[" + ids + "],\"deviceId\":\"" + owner.device() + "\"}"
                : "{\"kind\":\"GROUP\",\"title\":\"Plantão\",\"memberIds\":[" + ids + "],\"deviceId\":\"" + owner.device() + "\"}";
        MockHttpServletResponse r = owner.client().post("/api/v1/messaging/conversations", body);
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(201);
        return json(r).get("id").asString();
    }

    private static JsonNode claims(String token) throws Exception {
        return JSON.readTree(Base64.getUrlDecoder().decode(token.split("\\.")[1]));
    }

    @Test
    void ringAnswerAndHangUpKeepOnlyMetadata() throws Exception {
        Doc ana = doctor();
        Doc bruno = doctor();
        String cid = conversation(ana, bruno);

        assertThat(ana.client().post("/api/v1/calls", "{\"conversationId\":\"" + cid + "\"}").getStatus()).as("sem aparelho aprovado").isEqualTo(422);
        assertThat(ana.client().post("/api/v1/calls", "{\"conversationId\":\"" + cid + "\",\"deviceId\":\"" + bruno.device() + "\"}").getStatus())
                .as("aparelho de outra pessoa").isEqualTo(422);
        MockHttpServletResponse started = ana.client().post("/api/v1/calls", "{\"conversationId\":\"" + cid + "\",\"deviceId\":\"" + ana.device() + "\"}");
        assertThat(started.getStatus()).as(started.getContentAsString()).isEqualTo(201);
        JsonNode s = json(started);
        String callId = s.get("call").get("id").asString();
        assertThat(s.get("call").get("state").asString()).isEqualTo("RINGING");

        // admissão no SFU: token interno de 60 s, público rtc, com a sala e o aparelho
        String token = s.get("admission").get("token").asString();
        assertThat(tokens.verify(token, InternalTokens.RTC)).hasValueSatisfying(c -> {
            assertThat(c.sub()).isEqualTo(ana.id());
            assertThat(c.exp() - c.iat()).isEqualTo(60_000);
        });
        assertThat(tokens.verify(token, InternalTokens.CLINICAL)).isEmpty();
        assertThat(claims(token).get("room").asString()).isEqualTo(callId);
        assertThat(claims(token).get("device").asString()).isEqualTo(ana.device().toString());
        assertThat(s.get("admission").get("url").asString()).startsWith("ws");

        // TURN do coturn com credencial efêmera (usuário "validade:id", senha HMAC-SHA1)
        JsonNode ice = s.get("admission").get("iceServers").get(0);
        String user = ice.get("username").asString();
        long expiry = Long.parseLong(user.split(":")[0]);
        assertThat(expiry - System.currentTimeMillis() / 1000).isBetween(590L, 600L);
        assertThat(user).endsWith(":" + ana.id());
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec("segredo-de-teste-do-coturn".getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
        assertThat(ice.get("credential").asString()).isEqualTo(Base64.getEncoder().encodeToString(mac.doFinal(user.getBytes(StandardCharsets.UTF_8))));

        // Bruno vê a chamada tocando; quem não está na conversa, não
        assertThat(json(bruno.client().get("/api/v1/calls/incoming")).get("items").toString()).contains(callId);
        assertThat(json(ana.client().get("/api/v1/calls/incoming")).get("items")).isEmpty();
        Doc carla = doctor();
        assertThat(json(carla.client().get("/api/v1/calls/incoming")).get("items")).isEmpty();
        assertThat(carla.client().get("/api/v1/calls/" + callId).getStatus()).isEqualTo(404);
        assertThat(carla.client().post("/api/v1/calls/" + callId + "/join", "{\"deviceId\":\"" + carla.device() + "\"}").getStatus()).isEqualTo(404);

        // começar de novo na mesma conversa cai na mesma chamada
        assertThat(json(bruno.client().post("/api/v1/calls", "{\"conversationId\":\"" + cid + "\",\"deviceId\":\"" + bruno.device() + "\"}")).get("call").get("id")
                .asString()).isEqualTo(callId);
        JsonNode active = json(bruno.client().get("/api/v1/calls/" + callId));
        assertThat(active.get("state").asString()).isEqualTo("ACTIVE");
        assertThat(active.get("participants")).hasSize(2);

        assertThat(ana.client().post("/api/v1/calls/" + callId + "/leave", "{\"quality\":\"PERFEITA\"}").getStatus()).isEqualTo(422);
        assertThat(json(ana.client().post("/api/v1/calls/" + callId + "/leave", "{\"quality\":\"BOA\"}")).get("state").asString()).isEqualTo("ACTIVE");
        JsonNode ended = json(bruno.client().post("/api/v1/calls/" + callId + "/leave", "{\"quality\":\"REGULAR\",\"screenShared\":true}"));
        assertThat(ended.get("state").asString()).isEqualTo("ENDED");
        assertThat(jdbc.queryForObject("select quality from call_session where id = ?::uuid", String.class, callId)).as("a pior relatada").isEqualTo("REGULAR");
        assertThat(jdbc.queryForObject("select screen_shared from call_session where id = ?::uuid", Boolean.class, callId)).isTrue();
        assertThat(bruno.client().post("/api/v1/calls/" + callId + "/join", "{\"deviceId\":\"" + bruno.device() + "\"}").getStatus()).isEqualTo(410);
    }

    @Test
    void declinedMissedAndGroupLimits() throws Exception {
        Doc ana = doctor();
        Doc bruno = doctor();
        String cid = conversation(ana, bruno);
        String callId = json(ana.client().post("/api/v1/calls", "{\"conversationId\":\"" + cid + "\",\"deviceId\":\"" + ana.device() + "\"}")).get("call").get("id")
                .asString();
        assertThat(json(bruno.client().post("/api/v1/calls/" + callId + "/decline", null)).get("state").asString()).isEqualTo("DECLINED");

        String missed = json(ana.client().post("/api/v1/calls", "{\"conversationId\":\"" + cid + "\",\"deviceId\":\"" + ana.device() + "\"}")).get("call").get("id")
                .asString();
        jdbc.update("update call_session set created_at = now() - interval '2 minutes' where id = ?::uuid", missed);
        calls.expireRinging();
        assertThat(json(ana.client().get("/api/v1/calls/" + missed)).get("state").asString()).isEqualTo("MISSED");
        assertThat(json(bruno.client().get("/api/v1/calls/incoming")).get("items")).isEmpty();

        // MVP: até 8 participantes
        Doc[] many = new Doc[8];
        for (int i = 0; i < 8; i++) many[i] = doctor();
        String big = conversation(ana, many);
        MockHttpServletResponse tooMany = ana.client().post("/api/v1/calls", "{\"conversationId\":\"" + big + "\",\"deviceId\":\"" + ana.device() + "\"}");
        assertThat(tooMany.getStatus()).isEqualTo(422);
        assertThat(tooMany.getContentAsString()).contains("até 8");
    }
}
