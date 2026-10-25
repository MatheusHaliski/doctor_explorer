package com.doctorexplorer.clinical.documents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.doctorexplorer.clinical.catalog.MedicationCatalog;
import com.doctorexplorer.clinical.signature.PadesVerifier;
import com.doctorexplorer.clinical.support.StubCore;
import com.doctorexplorer.clinical.support.TestInfra;
import com.doctorexplorer.clinical.support.TestTokens;
import com.doctorexplorer.clinical.support.TestTokens.Doctor;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * RF09 ponta a ponta com PostgreSQL real e o provedor ICP-Brasil simulado: catálogo CMED com bloqueio de controle
 * especial, revisão pelo hash, assinatura PAdES-B-T com sudo, PDF/A conferido, validador público, link de uso único,
 * cancelamento que o validador mostra, modelos do médico e imutabilidade no banco.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Import(TestInfra.class)
class DocumentIT {

    static final Pattern ID = Pattern.compile("\"id\":\"([0-9a-f-]{36})\"");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired MedicationCatalog catalog;
    @Autowired PadesVerifier verifier;

    @BeforeEach
    void setUp() {
        StubCore.AUDIT.clear();
        StubCore.AUDIT_STATUS.set(201);
    }

    private MockHttpServletResponse call(MockHttpServletRequestBuilder b, String token, String json) throws Exception {
        if (token != null) b.header("Authorization", "DE-Internal " + token);
        if (json != null) b.contentType(MediaType.APPLICATION_JSON).content(json);
        MockHttpServletResponse r = mvc.perform(b).andReturn().getResponse();
        r.setCharacterEncoding("UTF-8");
        return r;
    }

    private static String field(String name, String body) {
        Matcher m = Pattern.compile("\"" + name + "\":\"([^\"]+)\"").matcher(body);
        assertThat(m.find()).as(name + " em " + body).isTrue();
        return m.group(1);
    }

    private static String w(Doctor d) {
        return TestTokens.token(d, true, false, "VERIFIED");
    }

    private static String sudo(Doctor d) {
        return TestTokens.token(d, true, true, "VERIFIED");
    }

    private static String r(Doctor d) {
        return TestTokens.token(d, false, false, "VERIFIED");
    }

    private String patient(Doctor d) throws Exception {
        return field("id", call(post("/patients"), w(d), "{\"name\":\"Helena Duarte Campos\",\"birthDate\":\"1964-03-02\"}").getContentAsString());
    }

    private String amoxicillin() {
        return catalog.search("amoxicilina 500 mg cap", 50).stream().filter(m -> m.antimicrobial() && !m.specialControl()).findFirst().orElseThrow().ggrem();
    }

    private String plain() {
        return catalog.search("dipirona monoidratada 500 mg com", 50).stream().filter(m -> !m.specialControl() && !m.antimicrobial()).findFirst().orElseThrow().ggrem();
    }

    private String controlled() {
        return catalog.search("alprazolam", 50).stream().filter(MedicationCatalog.Medication::specialControl).findFirst().orElseThrow().ggrem();
    }

    @Test
    void prescriptionIsReviewedSignedValidatedDeliveredOnceAndCanceled() throws Exception {
        Doctor ana = Doctor.random("Dra. Ana Ribeiro");
        String pid = patient(ana);
        MockHttpServletResponse created = call(post("/documents"), w(ana), "{\"patientId\":\"" + pid + "\",\"kind\":\"RECEITA\"}");
        assertThat(created.getStatus()).as(created.getContentAsString()).isEqualTo(201);
        String id = field("id", created.getContentAsString());

        // controle especial fica fora do MVP, com explicação; campos estruturados todos conferidos de uma vez
        MockHttpServletResponse blocked = call(put("/documents/" + id), w(ana), """
                {"medications":[{"ggrem":"%s","dose":"1","doseUnit":"comprimido","route":"ORAL","timesPerDay":1,"durationDays":30},
                                {"ggrem":"%s","dose":"um","doseUnit":"xícara","route":"BOCA","durationDays":0}]}""".formatted(controlled(), plain()));
        assertThat(blocked.getStatus()).isEqualTo(422);
        assertThat(blocked.getContentAsString()).contains("controle especial").contains("medications[1].dose").contains("medications[1].doseUnit")
                .contains("medications[1].route").contains("medications[1].frequency").contains("medications[1].durationDays");

        MockHttpServletResponse draft = call(put("/documents/" + id), w(ana), """
                {"medications":[{"ggrem":"%s","dose":"1","doseUnit":"cápsula","route":"ORAL","intervalHours":8,"durationDays":7,"quantity":"1 caixa"},
                                {"ggrem":"%s","dose":"1","doseUnit":"comprimido","route":"ORAL","intervalHours":6,"durationDays":3,"instructions":"Se dor ou febre."}],
                 "notes":"Retorno em 7 dias."}""".formatted(amoxicillin(), plain()));
        assertThat(draft.getStatus()).as(draft.getContentAsString()).isEqualTo(200);
        assertThat(draft.getContentAsString()).contains("1 cápsula por via oral, de 8 em 8 horas, por 7 dias.").contains("\"antimicrobial\":true");
        String reviewed = field("contentHash", draft.getContentAsString());

        assertThat(call(post("/documents/" + id + "/sign"), w(ana), "{\"reviewedHash\":\"" + reviewed + "\"}").getContentAsString()).contains("sudo-necessario");
        assertThat(call(post("/documents/" + id + "/sign"), sudo(ana), "{\"reviewedHash\":\"outro\"}").getContentAsString()).contains("revisao-desatualizada");
        MockHttpServletResponse signed = call(post("/documents/" + id + "/sign"), sudo(ana), "{\"reviewedHash\":\"" + reviewed + "\"}");
        assertThat(signed.getStatus()).as(signed.getContentAsString()).isEqualTo(200);
        String body = signed.getContentAsString();
        assertThat(body).contains("\"state\":\"ASSINADA\"").contains("\"signatureLevel\":\"PAdES-B-T\"").contains("\"signatureProvider\":\"SIMULADO\"")
                .contains("\"validUntil\":\"" + LocalDate.now(ZoneId.of("America/Sao_Paulo")).plusDays(10) + "\"");
        String code = field("validationCode", body);

        // imutável na API e no banco
        assertThat(call(put("/documents/" + id), w(ana), "{\"medications\":[]}").getContentAsString()).contains("documento-imutavel");
        try {
            jdbc.update("update clinical_document set valid_until = null where id = ?::uuid", id);
            throw new AssertionError("o gatilho deveria recusar");
        } catch (org.springframework.dao.DataAccessException e) {
            assertThat(e.getMostSpecificCause().getMessage()).contains("imutável");
        }

        // PDF assinado confere e é o mesmo que o validador público confere
        MockHttpServletResponse pdf = call(get("/documents/" + id + "/pdf"), r(ana), null);
        assertThat(pdf.getContentType()).startsWith("application/pdf");
        PadesVerifier.Check check = verifier.verify(pdf.getContentAsByteArray());
        assertThat(check.valid()).as(check.problems().toString()).isTrue();
        assertThat(check.crm()).isEqualTo(ana.crm() + ana.uf());

        MockHttpServletResponse publicView = call(get("/public/documents/" + code.toLowerCase().replace("-", " ")), TestTokens.publicToken(), null);
        assertThat(publicView.getStatus()).as(publicView.getContentAsString()).isEqualTo(200);
        assertThat(publicView.getContentAsString()).contains("\"status\":\"VALIDA\"").contains("\"patientInitials\":\"H. D. C.\"")
                .contains("\"doctorName\":\"Dra. Ana Ribeiro\"").contains("\"simulated\":true").contains("de 8 em 8 horas").doesNotContain("Helena");
        assertThat(call(get("/public/documents/AAAA-BBBB-CCCC-DDDD"), TestTokens.publicToken(), null).getStatus()).isEqualTo(404);
        assertThat(call(get("/public/documents/xyz"), TestTokens.publicToken(), null).getStatus()).isEqualTo(404);
        // o token PUBLIC não abre o prontuário, e o do médico não usa as rotas públicas
        assertThat(call(get("/patients"), TestTokens.publicToken(), null).getStatus()).isEqualTo(403);
        assertThat(call(get("/public/documents/" + code), r(ana), null).getStatus()).isEqualTo(403);

        // link de uso único para o paciente
        MockHttpServletResponse sent = call(post("/documents/" + id + "/deliver"), w(ana), "{\"channel\":\"EMAIL\",\"destination\":\"helena@example.org\"}");
        assertThat(sent.getStatus()).as(sent.getContentAsString()).isEqualTo(200);
        assertThat(sent.getContentAsString()).contains("\"destination\":\"h***@example.org\"");
        String token = field("link", sent.getContentAsString()).replaceAll(".*/receita/", "");
        MockHttpServletResponse first = call(get("/public/deliveries/" + token), TestTokens.publicToken(), null);
        assertThat(first.getStatus()).isEqualTo(200);
        assertThat(first.getContentAsByteArray()).isEqualTo(pdf.getContentAsByteArray());
        assertThat(call(get("/public/deliveries/" + token), TestTokens.publicToken(), null).getStatus()).as("uso único").isEqualTo(410);
        assertThat(call(get("/documents/" + id), r(ana), null).getContentAsString()).contains("\"state\":\"ENVIADA\"").contains("\"usedAt\"");

        // cancelamento com motivo; o validador mostra "cancelada" e links novos não abrem
        String second = field("link", call(post("/documents/" + id + "/deliver"), w(ana), "{\"channel\":\"LINK\"}").getContentAsString()).replaceAll(".*/receita/", "");
        assertThat(call(post("/documents/" + id + "/cancel"), w(ana), "{\"reason\":\"erro\"}").getStatus()).isEqualTo(422);
        assertThat(call(post("/documents/" + id + "/cancel"), w(ana), "{\"reason\":\"Dose errada; nova receita emitida\"}").getContentAsString())
                .contains("\"state\":\"CANCELADA\"");
        assertThat(call(get("/public/documents/" + code), TestTokens.publicToken(), null).getContentAsString()).contains("\"status\":\"CANCELADA\"")
                .doesNotContain("Dose errada");
        assertThat(call(get("/public/deliveries/" + second), TestTokens.publicToken(), null).getStatus()).isEqualTo(410);
        assertThat(call(post("/documents/" + id + "/deliver"), w(ana), "{\"channel\":\"LINK\"}").getStatus()).isEqualTo(409);

        String audit = String.join("\n", StubCore.AUDIT);
        assertThat(audit).contains("clinical.document_sign_start").contains("clinical.document_sign").contains("clinical.document_validate")
                .contains("clinical.document_delivery_open").contains("clinical.document_cancel").doesNotContain("Helena").doesNotContain(code);
    }

    @Test
    void examRequestNeedsIndicationAndCarriesLoincAndTuss() throws Exception {
        Doctor ana = Doctor.random("Dra. Ana Ribeiro");
        String pid = patient(ana);
        String id = field("id", call(post("/documents"), w(ana), "{\"patientId\":\"" + pid + "\",\"kind\":\"EXAME\"}").getContentAsString());
        assertThat(call(get("/catalog/exams?q=hemograma"), r(ana), null).getContentAsString()).contains("58410-2").contains("40304361");
        String draft = call(put("/documents/" + id), w(ana), "{\"exams\":[{\"loinc\":\"58410-2\"},{\"loinc\":\"1988-5\"},{\"loinc\":\"58410-2\"}]}").getContentAsString();
        assertThat(draft.split("\"loinc\":\"58410-2\"").length - 1).as("exame repetido entra uma vez").isEqualTo(1);
        MockHttpServletResponse noIndication = call(post("/documents/" + id + "/sign"), sudo(ana), "{\"reviewedHash\":\"" + field("contentHash", draft) + "\"}");
        assertThat(noIndication.getContentAsString()).contains("\"field\":\"indication\"");
        assertThat(call(get("/documents/" + id), r(ana), null).getContentAsString()).as("a recusa não muda o rascunho").contains("\"state\":\"RASCUNHO\"");
        draft = call(put("/documents/" + id), w(ana), "{\"exams\":[{\"loinc\":\"58410-2\"},{\"loinc\":\"1988-5\"}],\"indication\":\"Febre há 5 dias\"}").getContentAsString();
        MockHttpServletResponse signed = call(post("/documents/" + id + "/sign"), sudo(ana), "{\"reviewedHash\":\"" + field("contentHash", draft) + "\"}");
        assertThat(signed.getContentAsString()).contains("\"state\":\"ASSINADA\"").contains("\"tuss\":\"40308391\"").doesNotContain("\"validUntil\":\"2");
        assertThat(call(put("/documents/" + id), w(ana), "{\"medications\":[{\"ggrem\":\"x\"}]}").getStatus()).isEqualTo(409);
        // "Prescrições": os documentos do médico de todos os pacientes, sem os de outro médico
        assertThat(call(get("/documents"), r(ana), null).getContentAsString()).contains(id).contains("\"patientName\"").contains("\"items\":2");
        assertThat(call(get("/documents"), r(Doctor.random("Dr. Outro")), null).getContentAsString()).doesNotContain(id);
    }

    @Test
    void templatesAreTheDoctorsOwnAndHaveNoPatient() throws Exception {
        Doctor ana = Doctor.random("Dra. Ana Ribeiro");
        String body = """
                {"kind":"RECEITA","name":"Sinusite","content":{"medications":[{"ggrem":"%s","dose":"1","doseUnit":"cápsula","route":"ORAL","intervalHours":8,"durationDays":10}],"notes":"Helena"}}"""
                .formatted(amoxicillin());
        MockHttpServletResponse saved = call(post("/document-templates"), w(ana), body);
        assertThat(saved.getStatus()).as(saved.getContentAsString()).isEqualTo(201);
        assertThat(call(post("/document-templates"), w(ana), body).getStatus()).isEqualTo(409);
        String list = call(get("/document-templates?kind=RECEITA"), r(ana), null).getContentAsString();
        assertThat(list).contains("Sinusite").contains("\"intervalHours\":8").doesNotContain("Helena");
        Doctor bruno = Doctor.random("Dr. Bruno Lima");
        assertThat(call(get("/document-templates?kind=RECEITA"), r(bruno), null).getContentAsString()).doesNotContain("Sinusite");
        String tid = field("id", saved.getContentAsString());
        assertThat(call(delete("/document-templates/" + tid), w(bruno), null).getStatus()).isEqualTo(404);
        assertThat(call(delete("/document-templates/" + tid), w(ana), null).getStatus()).isEqualTo(204);
    }

    @Test
    void anotherVaultCannotSeeOrSign() throws Exception {
        Doctor ana = Doctor.random("Dra. Ana Ribeiro");
        String pid = patient(ana);
        String id = field("id", call(post("/documents"), w(ana), "{\"patientId\":\"" + pid + "\",\"kind\":\"RECEITA\"}").getContentAsString());
        Doctor bruno = Doctor.random("Dr. Bruno Lima");
        assertThat(call(get("/documents/" + id), r(bruno), null).getStatus()).isEqualTo(404);
        assertThat(call(post("/documents"), w(bruno), "{\"patientId\":\"" + pid + "\",\"kind\":\"RECEITA\"}").getStatus()).isEqualTo(404);
    }
}
