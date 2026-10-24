package com.doctorexplorer.core.privacy;

import static org.assertj.core.api.Assertions.assertThat;

import com.doctorexplorer.core.identity.domain.AccountStatus;
import com.doctorexplorer.core.identity.domain.UserAccount;
import com.doctorexplorer.core.identity.repo.UserAccounts;
import com.doctorexplorer.core.identity.repo.UserKeys;
import com.doctorexplorer.core.identity.service.LoggingMailer;
import com.doctorexplorer.core.privacy.service.ErasureSaga;
import com.doctorexplorer.core.support.ApiClient;
import com.doctorexplorer.core.support.TestInfra;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** RF10 · consentimentos, seus dados, exportação, exclusão com carência, reativação, saga de eliminação, Encarregado. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Import(TestInfra.class)
class PrivacyIT {

    static final String PASSWORD = "frase longa e segura 2026";

    @Autowired MockMvc mvc;
    @Autowired LoggingMailer mailer;
    @Autowired StringRedisTemplate redis;
    @Autowired UserAccounts accounts;
    @Autowired UserKeys userKeys;
    @Autowired ErasureSaga saga;

    ApiClient api;
    String email;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mvc);
        mailer.clear();
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
        email = "lgpd+" + System.nanoTime() + "@example.org";
        api.register(email, PASSWORD);
        api.activateEmail(mailer);
    }

    @Test
    void consentsStartOffAndEveryChangeIsRecorded() throws Exception {
        String initial = api.get("/api/v1/privacy/consents").getContentAsString();
        assertThat(initial).contains("LOCATION").contains("INSTITUTIONAL_COMMUNICATION").doesNotContain("\"granted\":true");

        assertThat(api.post("/api/v1/privacy/consents/location", null).getStatus()).isIn(405, 403); // só PUT
        MockHttpServletResponse on = put("/api/v1/privacy/consents/location", "{\"granted\":true}");
        assertThat(on.getStatus()).isEqualTo(200);
        assertThat(on.getContentAsString()).contains("\"granted\":true");
        put("/api/v1/privacy/consents/location", "{\"granted\":false}");

        String history = api.get("/api/v1/privacy/consents/history").getContentAsString();
        assertThat(history).contains("\"granted\":true").contains("\"granted\":false").contains("\"textVersion\":\"2026-10-24\"");
        assertThat(api.get("/api/v1/privacy/consents").getContentAsString()).doesNotContain("\"granted\":true");
        assertThat(put("/api/v1/privacy/consents/telepatia", "{\"granted\":true}").getStatus()).isEqualTo(404);
    }

    @Test
    void myDataAndExportInVersionedJson() throws Exception {
        String data = api.get("/api/v1/privacy/data").getContentAsString();
        assertThat(data).contains("\"formatVersion\":\"1\"").contains("Dra. Ana Ribeiro").contains("registrosClinicos").contains("compartilhamento");

        MockHttpServletResponse req = api.post("/api/v1/privacy/export", null);
        assertThat(req.getStatus()).isEqualTo(202);
        Matcher m = Pattern.compile("\"downloadUrl\":\"([^\"]+)\"").matcher(req.getContentAsString());
        assertThat(m.find()).isTrue();
        MockHttpServletResponse file = api.get(m.group(1));
        assertThat(file.getStatus()).isEqualTo(200);
        assertThat(file.getHeader("Content-Disposition")).contains("doctor-explorer-meus-dados-v1.json");
        assertThat(file.getContentAsString()).contains("\"conta\"").contains(email);

        ApiClient outra = new ApiClient(mvc);
        outra.register("outra+" + System.nanoTime() + "@example.org", PASSWORD);
        outra.activateEmail(mailer);
        assertThat(outra.get(m.group(1)).getStatus()).isEqualTo(404); // cópia de outro titular não existe para quem não é dono
    }

    @Test
    void deletionNeedsSudoThenOffersReactivationOnLogin() throws Exception {
        assertThat(api.post("/api/v1/me/deletion", null).getStatus()).isEqualTo(403);
        api.sudo(PASSWORD);
        MockHttpServletResponse del = api.post("/api/v1/me/deletion", null);
        assertThat(del.getStatus()).isEqualTo(200);
        assertThat(del.getContentAsString()).contains("PENDING_DELETION").contains("20 anos");
        assertThat(mailer.outbox().get(mailer.outbox().size() - 1).subject()).contains("Pedido de exclusão recebido");
        assertThat(api.get("/api/v1/me").getStatus()).isEqualTo(401); // sessão encerrada

        MockHttpServletResponse login = api.login(email, PASSWORD);
        assertThat(login.getContentAsString()).contains("\"next\":\"reactivate\"");
        assertThat(api.get("/api/v1/me").getStatus()).isEqualTo(401);
        assertThat(api.post("/api/v1/auth/reactivate", null).getStatus()).isEqualTo(200);
        assertThat(api.get("/api/v1/me").getContentAsString()).contains("\"status\":\"ACTIVE\"");
    }

    @Test
    void erasureSagaAnonymizesShredsKeyAndIsIdempotent() throws Exception {
        api.sudo(PASSWORD);
        api.post("/api/v1/me/deletion", null);
        UserAccount a = accounts.findByEmailNormalized(email).orElseThrow();

        // carência ainda vigente: nada acontece
        assertThat(saga.runDue(Instant.now())).isEmpty();

        // simula o fim da carência e roda o job
        var r1 = saga.erase(a.getId(), a.getGraceEndsAt().plus(1, ChronoUnit.HOURS));
        assertThat(r1.steps()).contains("user_key:destruida", "user_account:anonimizada", "valkey");
        UserAccount gone = accounts.findById(a.getId()).orElseThrow();
        assertThat(gone.getStatus()).isEqualTo(AccountStatus.ANONYMOUS_LGPD_DELETED);
        assertThat(gone.getEmail()).doesNotContain("lgpd+").endsWith("@anonymous.invalid");
        assertThat(gone.getDisplayName()).isEqualTo("Conta excluída");
        assertThat(userKeys.findById(a.getId()).orElseThrow().isDestroyed()).isTrue();
        assertThat(accounts.findByEmailNormalized(email)).isEmpty();

        // idempotente
        assertThat(saga.erase(a.getId(), Instant.now()).steps()).containsExactly("ja-eliminada");

        // login com o e-mail antigo: mensagem neutra, como para qualquer conta inexistente
        MockHttpServletResponse login = new ApiClient(mvc).login(email, PASSWORD);
        assertThat(login.getStatus()).isEqualTo(401);
        assertThat(login.getContentAsString()).contains("E-mail ou senha incorretos.");
    }

    @Test
    void dpoChannelGivesProtocolAndDeadline() throws Exception {
        MockHttpServletResponse t = api.post("/api/v1/privacy/dpo-requests", "{\"type\":\"SHARING_INFO\",\"message\":\"Com quem meus dados são compartilhados?\"}");
        assertThat(t.getStatus()).isEqualTo(201);
        assertThat(t.getContentAsString()).containsPattern("\"protocol\":\"DPO-\\d{8}-\\d{5}\"").contains("\"state\":\"OPEN\"").contains("dueAt");
        assertThat(api.post("/api/v1/privacy/dpo-requests", "{\"type\":\"OTHER\",\"message\":\"  \"}").getStatus()).isEqualTo(422);
        assertThat(api.get("/api/v1/privacy/dpo-requests").getContentAsString()).contains("SHARING_INFO");
        assertThat(api.get("/api/v1/privacy/dpo/tickets").getStatus()).isEqualTo(403); // só o Encarregado
    }

    private MockHttpServletResponse put(String path, String json) throws Exception {
        return api.put(path, json);
    }
}
