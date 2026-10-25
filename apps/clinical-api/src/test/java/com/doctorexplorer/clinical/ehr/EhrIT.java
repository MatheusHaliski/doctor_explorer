package com.doctorexplorer.clinical.ehr;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.doctorexplorer.clinical.support.StubCore;
import com.doctorexplorer.clinical.support.TestInfra;
import com.doctorexplorer.clinical.support.TestTokens;
import com.doctorexplorer.clinical.support.TestTokens.Doctor;
import java.nio.charset.StandardCharsets;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * RF08 ponta a ponta na clinical-api com PostgreSQL real: token interno, cofre por médico, paciente cifrado com
 * busca cega, rascunho, assinatura com sudo, imutabilidade (API e gatilho do banco), retificação com motivo e
 * auditoria de cada leitura e escrita (sem dado do paciente no evento).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Import(TestInfra.class)
class EhrIT {

    static final Pattern ID = Pattern.compile("\"id\":\"([0-9a-f-]{36})\"");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

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

    private static String w(Doctor d) {
        return TestTokens.token(d, true, false, "VERIFIED");
    }

    private static String r(Doctor d) {
        return TestTokens.token(d, false, false, "VERIFIED");
    }

    private static String sudo(Doctor d) {
        return TestTokens.token(d, true, true, "VERIFIED");
    }

    private static String id(String body) {
        Matcher m = ID.matcher(body);
        assertThat(m.find()).as(body).isTrue();
        return m.group(1);
    }

    private String newPatient(Doctor d, String name) throws Exception {
        MockHttpServletResponse res = call(post("/patients"), w(d), "{\"name\":\"" + name + "\",\"birthDate\":\"1964-03-02\",\"cpf\":\"529.982.247-25\",\"sex\":\"female\"}");
        assertThat(res.getStatus()).as(res.getContentAsString()).isEqualTo(201);
        return id(res.getContentAsString());
    }

    @Test
    void onlyTheCoreWithAValidTokenGetsIn() throws Exception {
        Doctor d = Doctor.random("Dra. Ana Ribeiro");
        assertThat(call(get("/patients"), null, null).getStatus()).isEqualTo(401);
        assertThat(call(post("/patients"), r(d), "{}").getStatus()).as("token de leitura não escreve").isEqualTo(403);
        assertThat(call(get("/patients"), TestTokens.token(d, false, false, "PENDING"), null).getContentAsString()).contains("selo-necessario");
        assertThat(call(get("/terminology/cid10?q=sepse"), TestTokens.token(d, false, false, "PENDING"), null).getStatus()).isEqualTo(200);
        assertThat(call(get("/actuator/health"), null, null).getStatus()).isEqualTo(200);
    }

    @Test
    void patientsAreEncryptedSearchableBlindlyAndPrivateToTheVault() throws Exception {
        Doctor ana = Doctor.random("Dra. Ana Ribeiro");
        MockHttpServletResponse bad = call(post("/patients"), w(ana), "{\"name\":\"Jo\",\"birthDate\":\"2999-01-01\",\"cpf\":\"111.111.111-11\"}");
        assertThat(bad.getStatus()).isEqualTo(422);
        assertThat(bad.getContentAsString()).contains("\"field\":\"name\"").contains("\"field\":\"birthDate\"").contains("\"field\":\"cpf\"");

        String pid = newPatient(ana, "Maria Conceição Albuquerque");
        assertThat(call(get("/patients").param("q", "mar albu"), r(ana), null).getContentAsString()).contains(pid).contains("***.982.247-**");
        assertThat(call(get("/patients?q=joana"), r(ana), null).getContentAsString()).contains("\"count\":0");

        // nada em claro no banco: nem nome, nem CPF, nem o termo buscado
        byte[] raw = jdbc.queryForObject("select name_enc from patient where id = ?::uuid", byte[].class, pid);
        assertThat(new String(raw, StandardCharsets.ISO_8859_1)).doesNotContain("Maria");
        assertThat(jdbc.queryForObject("select count(*) from patient_name_index where patient_id = ?::uuid", Integer.class, pid)).isGreaterThan(5);

        Doctor bruno = Doctor.random("Dr. Bruno Lima");
        assertThat(call(get("/patients/" + pid), r(bruno), null).getStatus()).as("outro cofre").isEqualTo(404);
        assertThat(call(get("/patients?q=maria"), r(bruno), null).getContentAsString()).contains("\"count\":0");

        // auditoria: busca sem o termo, criação e leitura com o id opaco; nunca o nome
        assertThat(StubCore.AUDIT).anyMatch(e -> e.contains("clinical.patient_create") && e.contains(pid));
        assertThat(StubCore.AUDIT).anyMatch(e -> e.contains("clinical.patient_search") && e.contains("\"results\":\"1\""));
        assertThat(String.join("\n", StubCore.AUDIT)).doesNotContain("Maria").doesNotContain("Albuquerque").doesNotContain("529.982").doesNotContain("albu");
    }

    @Test
    void soapNoteIsDraftedSignedImmutableAndAmendedWithReason() throws Exception {
        Doctor ana = Doctor.random("Dra. Ana Ribeiro");
        String pid = newPatient(ana, "Helena Duarte");
        String post = UUID.randomUUID().toString();
        MockHttpServletResponse created = call(post("/compositions"), w(ana), "{\"patientId\":\"" + pid + "\",\"links\":[{\"type\":\"POST\",\"ref\":\"" + post + "\"}]}");
        assertThat(created.getStatus()).as(created.getContentAsString()).isEqualTo(201);
        String cid = id(created.getContentAsString());
        assertThat(created.getContentAsString()).contains("\"state\":\"RASCUNHO\"").contains("\"context\":\"DISCUSSAO_DE_CASO\"").contains(post);

        MockHttpServletResponse badDraft = call(put("/compositions/" + cid), w(ana), """
                {"subjective":"Dispneia há 3 dias","assessment":{"text":"x","icd10":["J18.9","Z99.99"],"snomed":["abc"]}}""");
        assertThat(badDraft.getStatus()).isEqualTo(422);
        assertThat(badDraft.getContentAsString()).contains("Z99.99").contains("SNOMED");

        assertThat(call(put("/compositions/" + cid), w(ana), """
                {"subjective":"Dispneia há 3 dias","objective":"","assessment":{"text":"Pneumonia comunitária","icd10":["j189"]},"plan":""}""").getStatus()).isEqualTo(200);
        assertThat(call(post("/compositions/" + cid + "/sign"), w(ana), null).getContentAsString()).as("sem sudo").contains("sudo-necessario");
        MockHttpServletResponse incomplete = call(post("/compositions/" + cid + "/sign"), sudo(ana), null);
        assertThat(incomplete.getStatus()).isEqualTo(422);
        assertThat(incomplete.getContentAsString()).contains("\"field\":\"objective\"").contains("\"field\":\"plan\"");

        call(put("/compositions/" + cid), w(ana), """
                {"subjective":"Dispneia há 3 dias","objective":"SatO2 91%, crepitações em base direita","assessment":{"text":"Pneumonia comunitária","icd10":["J18.9"],"snomed":["233604007"]},"plan":"Amoxicilina-clavulanato 7 dias; retorno em 48 h"}""");
        MockHttpServletResponse signed = call(post("/compositions/" + cid + "/sign"), sudo(ana), null);
        assertThat(signed.getStatus()).as(signed.getContentAsString()).isEqualTo(200);
        assertThat(signed.getContentAsString()).contains("\"state\":\"ASSINADA\"").contains("\"integrity\":{\"valid\":true").contains("\"display\":\"Pneumonia não especificada\"");

        // imutável pela API e pelo banco
        MockHttpServletResponse edit = call(put("/compositions/" + cid), w(ana), "{\"subjective\":\"trocar\"}");
        assertThat(edit.getStatus()).isEqualTo(409);
        assertThat(edit.getContentAsString()).contains("nota-imutavel");
        assertThat(call(delete("/compositions/" + cid), w(ana), null).getStatus()).isEqualTo(409);
        try {
            jdbc.update("update soap_composition set icd10 = 'A00' where id = ?::uuid", cid);
            throw new AssertionError("o gatilho deveria recusar");
        } catch (org.springframework.dao.DataAccessException e) {
            assertThat(e.getMostSpecificCause().getMessage()).contains("imutável");
        }

        Doctor bruno = Doctor.random("Dr. Bruno Lima");
        assertThat(call(get("/compositions/" + cid), r(bruno), null).getStatus()).isEqualTo(404);

        // retificação: motivo obrigatório, nova versão; ao assinar, a anterior fica RETIFICADA e continua visível
        assertThat(call(post("/compositions/" + cid + "/amend"), w(ana), "{\"reason\":\"erro\"}").getStatus()).isEqualTo(422);
        MockHttpServletResponse v2 = call(post("/compositions/" + cid + "/amend"), w(ana), "{\"reason\":\"Dose do antibiótico corrigida após cultura\"}");
        assertThat(v2.getStatus()).isEqualTo(201);
        String v2id = id(v2.getContentAsString());
        assertThat(v2.getContentAsString()).contains("\"version\":2").contains("\"state\":\"RASCUNHO\"").contains("SatO2 91%");
        assertThat(call(post("/compositions/" + cid + "/amend"), w(ana), "{\"reason\":\"outra retificação qualquer\"}").getStatus()).isEqualTo(409);
        call(put("/compositions/" + v2id), w(ana), """
                {"subjective":"Dispneia há 3 dias","objective":"SatO2 91%, crepitações em base direita","assessment":{"text":"Pneumonia comunitária","icd10":["J18.9"]},"plan":"Ceftriaxona 7 dias conforme cultura"}""");
        assertThat(call(post("/compositions/" + v2id + "/sign"), sudo(ana), null).getContentAsString()).contains("\"state\":\"ASSINADA\"");

        String versions = call(get("/compositions/" + v2id + "/versions"), r(ana), null).getContentAsString();
        assertThat(versions).contains("\"version\":1").contains("\"state\":\"RETIFICADA\"").contains("\"supersededBy\":\"" + v2id + "\"")
                .contains("\"version\":2").contains("Dose do antibiótico corrigida").contains("Amoxicilina").contains("Ceftriaxona");
        assertThat(versions.split("\"valid\":true").length - 1).as("as duas versões conferem").isEqualTo(2);

        String detail = call(get("/patients/" + pid), r(ana), null).getContentAsString();
        assertThat(detail).contains(cid).contains(v2id);

        List<String> actions = StubCore.AUDIT.stream().map(e -> e.replaceAll(".*\"action\":\"([a-z_.]+)\".*", "$1")).toList();
        assertThat(actions).contains("clinical.composition_create", "clinical.composition_draft", "clinical.composition_sign", "clinical.composition_amend",
                "clinical.composition_versions", "clinical.patient_read");
        assertThat(String.join("\n", StubCore.AUDIT)).doesNotContain("Dispneia").doesNotContain("Helena");
    }

    @Test
    void firstUseInParallelCreatesOneVaultAndOnePractitioner() throws Exception {
        Doctor d = Doctor.random("Dr. Caio Prado");
        try (var pool = java.util.concurrent.Executors.newFixedThreadPool(6)) {
            var futures = new java.util.ArrayList<java.util.concurrent.Future<Integer>>();
            for (int i = 0; i < 6; i++) futures.add(pool.submit(() -> call(get("/patients"), r(d), null).getStatus()));
            for (var f : futures) assertThat(f.get()).isEqualTo(200);
        }
        assertThat(jdbc.queryForObject("select count(*) from clinical_vault where owner_id = ?::uuid", Integer.class, d.id().toString())).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from practitioner where id = ?::uuid", Integer.class, d.id().toString())).isEqualTo(1);
    }

    @Test
    void withoutAuditNothingIsReadOrWritten() throws Exception {
        Doctor ana = Doctor.random("Dra. Ana Ribeiro");
        newPatient(ana, "Paciente Sem Auditoria");
        Integer before = jdbc.queryForObject("select count(*) from patient", Integer.class);
        StubCore.AUDIT_STATUS.set(500);
        MockHttpServletResponse res = call(post("/patients"), w(ana), "{\"name\":\"Outra Pessoa\",\"birthDate\":\"1990-01-01\"}");
        assertThat(res.getStatus()).isEqualTo(503);
        assertThat(res.getContentAsString()).contains("auditoria-indisponivel");
        assertThat(jdbc.queryForObject("select count(*) from patient", Integer.class)).as("a escrita foi desfeita").isEqualTo(before);
        assertThat(call(get("/patients"), r(ana), null).getStatus()).as("leitura também falha fechada").isEqualTo(503);
    }
}
