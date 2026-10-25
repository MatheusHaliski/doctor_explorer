package com.doctorexplorer.core.internal;

import static org.assertj.core.api.Assertions.assertThat;

import com.doctorexplorer.core.audit.AuditService;
import com.doctorexplorer.core.identity.service.LoggingMailer;
import com.doctorexplorer.core.support.ApiClient;
import com.doctorexplorer.core.support.TestCrm;
import com.doctorexplorer.core.support.TestInfra;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * RF08 / ADR-008 · a core-api é a única porta do prontuário: sessão + CSRF viram um token interno de 60 s, o corpo
 * segue sem ser lido, e a clinical-api devolve a auditoria com o mesmo token. Aqui a clinical-api é um servidor de
 * mentira que guarda o que recebeu.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Import(TestInfra.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ClinicalGatewayIT {

    record Received(String method, String path, String authorization, String body) {}

    static final List<Received> RECEIVED = new CopyOnWriteArrayList<>();
    static final HttpServer CLINICAL;

    static {
        try {
            CLINICAL = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            CLINICAL.createContext("/", ex -> {
                String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                RECEIVED.add(new Received(ex.getRequestMethod(), ex.getRequestURI().toString(), ex.getRequestHeaders().getFirst("Authorization"), body));
                byte[] out = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().add("Content-Type", "application/json");
                ex.getResponseHeaders().add("X-Interno", "nao-repassar");
                ex.sendResponseHeaders(ex.getRequestMethod().equals("POST") ? 201 : 200, out.length);
                ex.getResponseBody().write(out);
                ex.close();
            });
            CLINICAL.start();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void clinical(DynamicPropertyRegistry r) {
        r.add("doctor-explorer.clinical.base-url", () -> "http://127.0.0.1:" + CLINICAL.getAddress().getPort());
    }

    @AfterAll
    static void stop() {
        CLINICAL.stop(0);
    }

    static final String PASSWORD = "frase longa e segura 2026";

    @Autowired MockMvc mvc;
    @Autowired LoggingMailer mailer;
    @Autowired StringRedisTemplate redis;
    @Autowired InternalTokens tokens;
    @Autowired AuditService audit;

    @BeforeEach
    void setUp() {
        RECEIVED.clear();
        mailer.clear();
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    private ApiClient verifiedDoctor() throws Exception {
        ApiClient c = new ApiClient(mvc);
        c.register("pep+" + System.nanoTime() + "@example.org", PASSWORD);
        c.activateEmail(mailer);
        c.post("/api/v1/credentials", "{\"crmNumber\":\"" + TestCrm.next("12") + "\",\"crmUf\":\"PR\",\"specialties\":[]}");
        return c;
    }

    @Test
    void sessionBecomesAShortInternalTokenAndTheBodyPassesThrough() throws Exception {
        assertThat(new ApiClient(mvc).get("/api/v1/clinical/patients").getStatus()).as("sem sessão").isEqualTo(401);

        ApiClient c = verifiedDoctor();
        String me = c.get("/api/v1/me").getContentAsString().replaceAll(".*\"id\":\"([0-9a-f-]{36})\".*", "$1");
        MockHttpServletResponse read = c.get("/api/v1/clinical/patients?q=maria");
        assertThat(read.getStatus()).isEqualTo(200);
        assertThat(read.getHeader("X-Interno")).as("cabeçalhos internos não voltam").isNull();
        assertThat(read.getHeader("Cache-Control")).isEqualTo("no-store");
        Received r = RECEIVED.get(0);
        assertThat(r.path()).isEqualTo("/patients?q=maria");
        assertThat(r.authorization()).startsWith("DE-Internal v1.");
        var claims = tokens.verify(r.authorization().substring(12), InternalTokens.CLINICAL).orElseThrow();
        assertThat(claims.sub().toString()).isEqualTo(me);
        assertThat(claims.seal()).isEqualTo("VERIFIED");
        assertThat(claims.write()).as("GET não ganha token de escrita").isFalse();
        assertThat(claims.exp() - claims.iat()).isEqualTo(60_000);
        assertThat(tokens.verify(r.authorization().substring(12), "outro-servico")).isEmpty();

        MockHttpServletResponse created = c.post("/api/v1/clinical/patients", "{\"name\":\"Maria Souza\",\"birthDate\":\"1970-01-01\"}");
        assertThat(created.getStatus()).isEqualTo(201);
        Received w = RECEIVED.get(1);
        assertThat(w.body()).contains("Maria Souza");
        assertThat(tokens.verify(w.authorization().substring(12), InternalTokens.CLINICAL).orElseThrow().write()).isTrue();
    }

    @Test
    void onlyKnownClinicalResourcesAreForwarded() throws Exception {
        ApiClient c = verifiedDoctor();
        assertThat(c.get("/api/v1/clinical/actuator/env").getStatus()).isEqualTo(404);
        assertThat(c.get("/api/v1/clinical/patients/..%2Factuator").getStatus()).isIn(400, 404);
        assertThat(c.get("/api/v1/clinical/fhir/r4/Provenance?target=Composition/abc").getStatus()).isEqualTo(200);
        assertThat(RECEIVED).singleElement().satisfies(r -> assertThat(r.path()).isEqualTo("/fhir/r4/Provenance?target=Composition/abc"));
    }

    @Test
    void writesStillNeedCsrf() throws Exception {
        ApiClient c = verifiedDoctor();
        MockHttpServletResponse noCsrf = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/clinical/patients")
                .content("{}").contentType("application/json")).andReturn().getResponse();
        assertThat(noCsrf.getStatus()).isIn(401, 403);
        assertThat(RECEIVED).isEmpty();
    }

    /** Roda por último: derruba a clinical-api de mentira. */
    @Test
    @Order(Integer.MAX_VALUE)
    void unreachableClinicalApiIs503AndNothingIsWritten() throws Exception {
        ApiClient c = verifiedDoctor();
        CLINICAL.stop(0);
        MockHttpServletResponse down = c.get("/api/v1/clinical/patients");
        assertThat(down.getStatus()).isEqualTo(503);
        assertThat(down.getContentAsString()).contains("prontuario-indisponivel");
    }

    @Test
    void auditCallbackAcceptsOnlyTheInternalTokenAndClinicalActions() throws Exception {
        UUID doctor = UUID.randomUUID();
        String token = tokens.issue(doctor, "Dra. Ana Ribeiro", "123456", "PR", "VERIFIED", 0, false, true, InternalTokens.CLINICAL);
        String body = "{\"action\":\"clinical.composition_sign\",\"resourceType\":\"composition\",\"resourceId\":\"c-1\",\"patientRef\":\"pat-9\",\"details\":{\"version\":\"1\"}}";
        var ok = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/internal/audit")
                .header("Authorization", "DE-Internal " + token).contentType("application/json").content(body)).andReturn().getResponse();
        assertThat(ok.getStatus()).isEqualTo(201);

        var noToken = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/internal/audit")
                .contentType("application/json").content(body)).andReturn().getResponse();
        assertThat(noToken.getStatus()).isEqualTo(401);
        var other = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/internal/audit")
                .header("Authorization", "DE-Internal " + token).contentType("application/json")
                .content("{\"action\":\"auth.login\"}")).andReturn().getResponse();
        assertThat(other.getStatus()).as("só clinical.*").isEqualTo(400);

        var viewer = new AuditService.Viewer(UUID.randomUUID(), "it", null, true, List.of("platform"), null);
        var found = audit.search(viewer, new AuditService.Query(null, null, doctor, "clinical.", null, null, false, 0, 50)).events();
        assertThat(found).singleElement().satisfies(e -> {
            assertThat(e.action()).isEqualTo("clinical.composition_sign");
            assertThat(e.patientRef()).isEqualTo("[oculto]");
            assertThat(e.details()).containsEntry("via", "clinical-api").containsEntry("version", "1");
        });
        assertThat(new ApiClient(mvc).get("/api/v1/internal/public-key").getContentAsString()).contains("\"spki\"").contains("\"ttlSeconds\":60");
    }
}
