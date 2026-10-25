package com.doctorexplorer.core.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.doctorexplorer.core.audit.AuditLog;
import com.doctorexplorer.core.audit.AuditRecord;
import com.doctorexplorer.core.audit.AuditService;
import com.doctorexplorer.core.audit.CassandraAuditStore;
import com.doctorexplorer.core.credentialing.domain.UserRole;
import com.doctorexplorer.core.credentialing.repo.UserRoles;
import com.doctorexplorer.core.identity.repo.UserAccounts;
import com.doctorexplorer.core.identity.service.LoggingMailer;
import com.doctorexplorer.core.support.ApiClient;
import com.doctorexplorer.core.support.MfaLogin;
import com.doctorexplorer.core.support.TestInfra;
import com.datastax.oss.driver.api.core.CqlSession;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
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
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** RF13 · auditoria encadeada (Cassandra), exportação assinada (S3), engajamento k-anônimo, qualidade e recortes de acesso. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Import(TestInfra.class)
class AuditDashboardIT {

    static final String PASSWORD = "frase longa e segura 2026";
    static final Pattern ID = Pattern.compile("\"id\":\"([0-9a-f-]{36})\"");
    static final String[] UFS = {"AC", "AL", "AP", "AM", "BA", "CE", "DF", "ES", "GO", "MA", "MT", "MS", "MG", "PA", "PB", "PE", "PI", "RJ", "RN", "RS", "RO"};

    @Autowired MockMvc mvc;
    @Autowired LoggingMailer mailer;
    @Autowired StringRedisTemplate redis;
    @Autowired UserAccounts accounts;
    @Autowired UserRoles roles;
    @Autowired AuditLog auditLog;
    @Autowired CassandraAuditStore store;
    @Autowired AuditService auditService;
    @Autowired CqlSession cql;

    @BeforeEach
    void setUp() {
        mailer.clear();
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    private ApiClient withRole(UserRole.Role role) throws Exception {
        String email = role.name().toLowerCase() + "+" + System.nanoTime() + "@example.org";
        ApiClient c = new ApiClient(mvc);
        c.register(email, PASSWORD);
        c.activateEmail(mailer);
        roles.save(new UserRole(accounts.findByEmailNormalized(email).orElseThrow().getId(), role, null));
        MfaLogin.enableAndRelogin(c, email, PASSWORD);
        return c;
    }

    private ApiClient verifiedDoctor(String tag, String uf) throws Exception {
        ApiClient c = new ApiClient(mvc);
        c.register(tag + "+" + System.nanoTime() + "@example.org", PASSWORD);
        c.activateEmail(mailer);
        assertThat(c.post("/api/v1/credentials", "{\"crmNumber\":\"12" + String.format("%04d", System.nanoTime() % 10_000) + "\",\"crmUf\":\"" + uf
                + "\",\"specialties\":[{\"specialtyCode\":\"UTI\",\"rqe\":\"11111\"}]}").getContentAsString()).contains("\"seal\":\"VERIFIED\"");
        return c;
    }

    private static String id(String body) {
        Matcher m = ID.matcher(body);
        assertThat(m.find()).as(body).isTrue();
        return m.group(1);
    }

    @Test
    void rolesSeeTheirSliceAndEveryViewIsAudited() throws Exception {
        ApiClient author = verifiedDoctor("autor", "SE");
        author.post("/api/v1/groups/uti/join", null);
        String postId = id(author.post("/api/v1/posts", "{\"specialtyCode\":\"UTI\",\"kind\":\"EDUCATION\",\"audience\":\"NETWORK\",\"title\":\"Revisão de sepse\",\"question\":\"Leitura guiada\"}").getContentAsString());

        ApiClient mod = withRole(UserRole.Role.MODERATOR);
        assertThat(mod.post("/api/v1/admin/posts/" + postId + "/hide", "{\"reason\":\"fora do escopo do grupo\"}").getStatus()).isEqualTo(200);

        String modView = mod.get("/api/v1/admin/audit").getContentAsString();
        assertThat(modView).contains("\"action\":\"moderation.post_hide\"").contains("\"intact\":true");
        assertThat(modView).doesNotContain("\"category\":\"auth\"").doesNotContain("\"category\":\"privacy\"");

        ApiClient dpo = withRole(UserRole.Role.DPO);
        String dpoView = dpo.get("/api/v1/admin/audit?action=audit.view").getContentAsString();
        assertThat(dpoView).as("a consulta do moderador ficou registrada").contains("\"action\":\"audit.view\"").contains("platform:moderator");
        assertThat(dpo.get("/api/v1/admin/audit?action=auth.login").getContentAsString()).contains("\"outcome\":\"success\"");

        // sem papel: 403; papel sem 2FA não chega aqui (MfaRequiredAuthorizationManager)
        assertThat(author.get("/api/v1/admin/audit").getStatus()).isEqualTo(403);
        // período maior que 31 dias: 422 com o campo
        assertThat(dpo.get("/api/v1/admin/audit?from=2026-01-01&to=2026-03-01").getContentAsString()).contains("\"field\":\"to\"");
    }

    @Test
    void tamperingIsDetectedOnRead() {
        String scope = "it:" + UUID.randomUUID();
        for (int i = 0; i < 4; i++) store.append(scope, AuditRecord.of("moderation.post_hide").resource("case_post", "p" + i), UUID.randomUUID(), Instant.now());
        AuditService.Viewer viewer = new AuditService.Viewer(UUID.randomUUID(), "it", null, false, List.of(scope), null);
        AuditService.Query q = new AuditService.Query(null, null, null, null, null, null, false, 0, 50);
        assertThat(auditService.search(viewer, q).chain().intact()).isTrue();

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        var row = cql.execute("SELECT seq, id FROM de_audit.audit_event WHERE scope = ? AND day = ? AND seq = 3", scope, today).one();
        cql.execute("UPDATE de_audit.audit_event SET resource_id = 'adulterado' WHERE scope = ? AND day = ? AND seq = ? AND id = ?",
                scope, today, row.getLong("seq"), row.getUuid("id"));
        var chain = auditService.search(viewer, q).chain();
        assertThat(chain.intact()).isFalse();
        assertThat(chain.broken().get(0)).containsEntry("seq", 3L).containsEntry("reason", "hash não confere: evento alterado");
    }

    @Test
    void signedExportVerifiesWithThePublicKey() throws Exception {
        ApiClient dpo = withRole(UserRole.Role.DPO);
        assertThat(dpo.post("/api/v1/admin/audit/exports", "{\"justification\":\"curta\"}").getStatus()).isEqualTo(422);

        ApiClient admin = withRole(UserRole.Role.ADMIN);
        assertThat(admin.post("/api/v1/admin/audit/exports", "{\"justification\":\"pedido da diretoria técnica\",\"revealPatient\":true}").getStatus()).isEqualTo(403);

        MockHttpServletResponse created = dpo.post("/api/v1/admin/audit/exports", "{\"action\":\"auth.\",\"justification\":\"inspeção trimestral da ANPD\"}");
        assertThat(created.getStatus()).isEqualTo(201);
        assertThat(created.getContentAsString()).contains("\"state\":\"PRONTA\"");
        String exportId = id(created.getContentAsString());

        assertThat(admin.get("/api/v1/audit/exports/" + exportId + "/download").getStatus()).as("só quem pediu baixa").isEqualTo(404);
        String dl = dpo.get("/api/v1/audit/exports/" + exportId + "/download").getContentAsString();
        assertThat(dl).contains("\"state\":\"BAIXADA\"").contains("\"urlTtlSeconds\":900");
        String csvUrl = group(Pattern.compile("\"csvUrl\":\"([^\"]+)\""), dl).replace("\\u0026", "&");
        String sig = group(Pattern.compile("\"signature\":\"([^\"]+)\""), dl);

        HttpResponse<byte[]> csv = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(csvUrl)).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        assertThat(csv.statusCode()).isEqualTo(200);
        String text = new String(csv.body(), java.nio.charset.StandardCharsets.UTF_8);
        assertThat(text).startsWith("# Doctor Explorer · exportação de auditoria · cadeia íntegra").contains("auth.login");

        String pem = new ApiClient(mvc).get("/api/v1/audit/public-key").getContentAsString(); // sem login
        byte[] der = Base64.getMimeDecoder().decode(pem.replace("-----BEGIN PUBLIC KEY-----", "").replace("-----END PUBLIC KEY-----", "").trim());
        PublicKey pub = KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(der));
        Signature verifier = Signature.getInstance("Ed25519");
        verifier.initVerify(pub);
        verifier.update(csv.body());
        assertThat(verifier.verify(Base64.getDecoder().decode(sig))).as("assinatura Ed25519 confere com a chave pública").isTrue();

        assertThat(dpo.get("/api/v1/admin/audit?action=audit.export").getContentAsString()).contains("inspeção trimestral da ANPD");
    }

    @Test
    void engagementIsAggregatedWithMinimumCellOfTen() throws Exception {
        for (int i = 0; i < 10; i++) {
            ApiClient a = verifiedDoctor("eng" + i, UFS[i]);
            a.post("/api/v1/groups/uti/join", null);
            assertThat(a.post("/api/v1/posts", "{\"specialtyCode\":\"UTI\",\"kind\":\"CASE\",\"audience\":\"NETWORK\",\"title\":\"Caso de engajamento " + i
                    + "\",\"complaint\":\"Paciente de 50 anos\",\"question\":\"Conduta?\"}").getStatus()).isEqualTo(201);
        }
        ApiClient admin = withRole(UserRole.Role.ADMIN);
        String eng = admin.get("/api/v1/admin/dashboard/engagement?days=7").getContentAsString();
        assertThat(eng).contains("\"minCell\":10").contains("\"casesPublished\":{\"value\":").contains("\"activeUsers\":{\"value\":");
        assertThat(eng).doesNotContain("\"casesPublished\":{\"value\":null");
        assertThat(eng).contains("\"weekly\":[");

        String quality = admin.get("/api/v1/admin/dashboard/quality").getContentAsString();
        assertThat(quality).contains("\"source\":\"docs/usabilidade/AVALIACAO_MVP-1.md\"").contains("\"preliminary\":true")
                .contains("\"releaseBlockers\":0").contains("\"p95Millis\"").contains("\"openIncidents\"");
    }

    @Test
    void institutionPanelNeedsTreeRoleAndMfaAndOnlyAuditorRevealsPatient() throws Exception {
        String email = "gestor+" + System.nanoTime() + "@example.org";
        ApiClient gestor = new ApiClient(mvc);
        gestor.register(email, PASSWORD);
        gestor.activateEmail(mailer);
        String suffix = String.format("%05d", System.nanoTime() % 100_000);
        String hospital = id(gestor.post("/api/v1/institutions", """
                {"legalName":"Hospital Painel LTDA","tradeName":"Hospital Painel","cnpj":"12%s","cnes":"12%s","type":"HOSPITAL","city":"Curitiba","uf":"PR"}
                """.formatted((suffix + "000000000000").substring(0, 12), suffix)).getContentAsString());

        MockHttpServletResponse noMfa = gestor.get("/api/v1/institutions/" + hospital + "/dashboard/engagement");
        assertThat(noMfa.getStatus()).isEqualTo(403);
        assertThat(noMfa.getContentAsString()).contains("2fa-necessario");
        MfaLogin.enableAndRelogin(gestor, email, PASSWORD);

        String eng = gestor.get("/api/v1/institutions/" + hospital + "/dashboard/engagement").getContentAsString();
        assertThat(eng).as("um só vínculo: tudo suprimido").contains("\"activeUsers\":{\"value\":null,\"suppressed\":true}");

        // evento clínico sintético com paciente, na árvore do hospital
        auditLog.record(AuditRecord.of("clinical.composition_read").resource("composition", "c-1").patient("pat-123").institution(UUID.fromString(hospital)));
        String masked = gestor.get("/api/v1/institutions/" + hospital + "/dashboard/audit").getContentAsString();
        assertThat(masked).contains("institution.register").contains("\"patientRef\":\"[oculto]\"").doesNotContain("pat-123");
        assertThat(gestor.get("/api/v1/institutions/" + hospital + "/dashboard/audit?revealPatient=true&justification=conferir%20acesso%20indevido")
                .getStatus()).as("gestor não revela paciente").isEqualTo(403);

        // auditor: vínculo pelos dois lados, papel AUDITOR, 2FA
        String auditorEmail = "auditor+" + System.nanoTime() + "@example.org";
        ApiClient auditor = new ApiClient(mvc);
        auditor.register(auditorEmail, PASSWORD);
        auditor.activateEmail(mailer);
        auditor.post("/api/v1/credentials", "{\"crmNumber\":\"12" + String.format("%04d", System.nanoTime() % 10_000) + "\",\"crmUf\":\"TO\",\"specialties\":[]}");
        String auditorId = id(auditor.get("/api/v1/me").getContentAsString());
        auditor.post("/api/v1/institutions/" + hospital + "/join", null);
        gestor.post("/api/v1/institutions/" + hospital + "/members/" + auditorId, null);
        assertThat(gestor.put("/api/v1/institutions/" + hospital + "/members/" + auditorId + "/role", "{\"role\":\"AUDITOR\"}").getStatus()).isEqualTo(200);
        MfaLogin.enableAndRelogin(auditor, auditorEmail, PASSWORD);

        assertThat(auditor.get("/api/v1/institutions/" + hospital + "/dashboard/audit?revealPatient=true").getContentAsString())
                .contains("\"field\":\"justification\"");
        String revealed = auditor.get("/api/v1/institutions/" + hospital + "/dashboard/audit?revealPatient=true&justification=conferir%20acesso%20indevido")
                .getContentAsString();
        assertThat(revealed).contains("\"patientRef\":\"pat-123\"").contains("\"patientRevealed\":true");
        assertThat(gestor.get("/api/v1/institutions/" + hospital + "/dashboard/audit?action=audit.view").getContentAsString())
                .as("a revelação ficou registrada com a justificativa").contains("conferir acesso indevido");

        ApiClient outsider = verifiedDoctor("fora", "RR");
        assertThat(outsider.get("/api/v1/institutions/" + hospital + "/dashboard/audit").getStatus()).isEqualTo(404);
    }

    private static String group(Pattern p, String body) {
        Matcher m = p.matcher(body);
        assertThat(m.find()).as(body).isTrue();
        return m.group(1);
    }
}
