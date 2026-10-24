package com.doctorexplorer.core.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.doctorexplorer.core.access.AccessMatrixIT;
import com.doctorexplorer.core.credentialing.domain.UserRole;
import com.doctorexplorer.core.credentialing.repo.ProfessionalProfiles;
import com.doctorexplorer.core.credentialing.repo.UserRoles;
import com.doctorexplorer.core.credentialing.service.CredentialingService;
import com.doctorexplorer.core.identity.repo.UserAccounts;
import com.doctorexplorer.core.identity.service.LoggingMailer;
import com.doctorexplorer.core.identity.service.Totp;
import com.doctorexplorer.core.support.ApiClient;
import com.doctorexplorer.core.support.TestInfra;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

/** RF02 (US06) + RF04 · fila do moderador: revisão manual de selo e aprovação de entrada em grupo. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Import(TestInfra.class)
class ModerationIT {

    static final String PASSWORD = "frase longa e segura 2026";
    static final Pattern ID = Pattern.compile("\"id\":\"([0-9a-f-]{36})\"");

    @Autowired MockMvc mvc;
    @Autowired LoggingMailer mailer;
    @Autowired StringRedisTemplate redis;
    @Autowired UserAccounts accounts;
    @Autowired UserRoles roles;
    @Autowired ProfessionalProfiles profiles;
    @Autowired CredentialingService credentialing;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        mailer.clear();
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    private ApiClient activeAccount(String tag) throws Exception {
        ApiClient c = new ApiClient(mvc);
        c.register(tag + "+" + System.nanoTime() + "@example.org", PASSWORD);
        c.activateEmail(mailer);
        return c;
    }

    private static String crm(String prefix) {
        return prefix + String.format("%04d", System.nanoTime() % 10_000);
    }

    /** Moderador: papel global + 2FA ativo + login em duas etapas (a rota /admin exige os dois). */
    private ApiClient moderator() throws Exception {
        String email = "mod+" + System.nanoTime() + "@example.org";
        ApiClient c = new ApiClient(mvc);
        c.register(email, PASSWORD);
        c.activateEmail(mailer);
        var account = accounts.findByEmailNormalized(email).orElseThrow();
        roles.save(new UserRole(account.getId(), UserRole.Role.MODERATOR, null));
        c.post("/api/v1/me/sudo", "{\"password\":\"" + PASSWORD + "\"}");
        String setup = c.post("/api/v1/me/mfa/totp/setup", null).getContentAsString();
        Matcher m = Pattern.compile("\"secret\":\"([A-Z2-7]+)\"").matcher(setup);
        assertThat(m.find()).isTrue();
        byte[] secret = AccessMatrixIT.base32Decode(m.group(1));
        assertThat(c.post("/api/v1/me/mfa/totp/confirm", "{\"code\":\"" + Totp.codeAt(secret, Instant.now().getEpochSecond()) + "\"}").getStatus()).isEqualTo(200);
        c.post("/api/v1/auth/logout", null);
        c.login(email, PASSWORD);
        assertThat(c.post("/api/v1/auth/login/2fa", "{\"code\":\"" + Totp.codeAt(secret, Instant.now().getEpochSecond()) + "\"}").getStatus()).isEqualTo(200);
        assertThat(c.get("/api/v1/auth/session").getContentAsString()).contains("\"roles\":[\"MODERATOR\",\"PROFESSIONAL\"]");
        return c;
    }

    /** Fonte fora do ar (prefixo 00) por mais de 24 h → MANUAL_REVIEW (o relógio é adiantado no banco). */
    private UUID doctorInManualReview() throws Exception {
        ApiClient doc = activeAccount("ana");
        UUID userId = UUID.fromString(group(ID, doc.get("/api/v1/me").getContentAsString()));
        assertThat(doc.post("/api/v1/credentials", "{\"crmNumber\":\"" + crm("00") + "\",\"crmUf\":\"PR\",\"specialties\":[{\"specialtyCode\":\"UTI\",\"rqe\":\"11111\"}]}")
                .getContentAsString()).contains("\"seal\":\"PENDING\"");
        jdbc.update("update professional_profile set seal_changed_at = now() - interval '25 hours' where user_id = ?", userId);
        credentialing.verify(profiles.findById(userId).orElseThrow());
        assertThat(doc.get("/api/v1/credentials").getContentAsString()).contains("\"seal\":\"MANUAL_REVIEW\"");
        return userId;
    }

    @Test
    void moderatorApprovesSealInManualReviewWithEvidenceAndEmail() throws Exception {
        UUID userId = doctorInManualReview();
        ApiClient mod = moderator();

        String queue = mod.get("/api/v1/admin/moderation/queue").getContentAsString();
        assertThat(queue).contains(userId.toString()).contains("\"seal\":\"MANUAL_REVIEW\"").contains("\"userName\":\"Dra. Ana Ribeiro\"").contains("UNAVAILABLE");

        mailer.clear();
        MockHttpServletResponse res = mod.post("/api/v1/admin/moderation/seals/" + userId + "/approve", "{\"reason\":\"Carteira do CRM conferida por videochamada.\"}");
        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(res.getContentAsString()).contains("\"seal\":\"VERIFIED\"").contains("Aprovado em revisão manual").contains("\"source\":\"moderador\"");
        assertThat(mailer.outbox()).hasSize(1);
        assertThat(mailer.outbox().get(0).subject()).isEqualTo("Seu CRM foi verificado");

        // some da fila; segunda decisão não encontra nada a revisar
        assertThat(mod.get("/api/v1/admin/moderation/queue").getContentAsString()).doesNotContain(userId.toString());
        assertThat(mod.post("/api/v1/admin/moderation/seals/" + userId + "/approve", null).getStatus()).isEqualTo(404);
    }

    @Test
    void rejectionRequiresAReasonTheDoctorCanRead() throws Exception {
        UUID userId = doctorInManualReview();
        ApiClient mod = moderator();
        MockHttpServletResponse semMotivo = mod.post("/api/v1/admin/moderation/seals/" + userId + "/reject", "{\"reason\":\"não\"}");
        assertThat(semMotivo.getStatus()).isEqualTo(422);
        assertThat(semMotivo.getContentAsString()).contains("\"field\":\"reason\"");

        MockHttpServletResponse res = mod.post("/api/v1/admin/moderation/seals/" + userId + "/reject", "{\"reason\":\"Documento enviado está ilegível; reenvie uma foto nítida.\"}");
        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(res.getContentAsString()).contains("\"seal\":\"REJECTED\"").contains("Recusado em revisão manual: Documento");
        assertThat(mailer.outbox().stream().anyMatch(s -> s.subject().equals("Não foi possível verificar seu CRM") && s.body().contains("ilegível"))).isTrue();
    }

    @Test
    void moderatorApprovesGroupRequestsFromTheQueueAndOthersAreRefused() throws Exception {
        // médica verificada sem RQE de dermatologia pede entrada no grupo DERM
        ApiClient doc = activeAccount("derm");
        UUID userId = UUID.fromString(group(ID, doc.get("/api/v1/me").getContentAsString()));
        doc.post("/api/v1/credentials", "{\"crmNumber\":\"" + crm("12") + "\",\"crmUf\":\"PR\",\"specialties\":[]}");
        assertThat(doc.post("/api/v1/groups/derm/join", null).getContentAsString()).contains("\"state\":\"REQUESTED\"");

        // médico comum (sem papel) não entra na fila
        assertThat(doc.get("/api/v1/admin/moderation/queue").getStatus()).isEqualTo(403);

        ApiClient mod = moderator();
        assertThat(mod.get("/api/v1/admin/moderation/queue").getContentAsString()).contains("\"specialtyCode\":\"DERM\"").contains(userId.toString());
        assertThat(mod.post("/api/v1/admin/moderation/groups/derm/members/" + userId + "/approve", null).getContentAsString()).contains("\"state\":\"ACTIVE\"");
        assertThat(doc.get("/api/v1/groups").getContentAsString()).contains("\"specialtyCode\":\"DERM\",\"name\"");
        // outros testes podem ter deixado pedidos de DERM de outras contas; o desta conta some da fila
        assertThat(mod.get("/api/v1/admin/moderation/queue").getContentAsString()).doesNotContain(userId.toString());
    }

    private static String group(Pattern p, String body) {
        Matcher m = p.matcher(body);
        assertThat(m.find()).isTrue();
        return m.group(1);
    }
}
