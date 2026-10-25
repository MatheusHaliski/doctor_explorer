package com.doctorexplorer.clinical.fhir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.ResultSeverityEnum;
import ca.uhn.fhir.validation.ValidationResult;
import com.doctorexplorer.clinical.catalog.MedicationCatalog;
import com.doctorexplorer.clinical.support.TestInfra;
import com.doctorexplorer.clinical.support.TestTokens;
import com.doctorexplorer.clinical.support.TestTokens.Doctor;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.hl7.fhir.common.hapi.validation.support.CommonCodeSystemsTerminologyService;
import org.hl7.fhir.common.hapi.validation.support.InMemoryTerminologyServerValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.SnapshotGeneratingValidationSupport;
import org.hl7.fhir.common.hapi.validation.support.ValidationSupportChain;
import org.hl7.fhir.common.hapi.validation.validator.FhirInstanceValidator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import ca.uhn.fhir.context.support.DefaultProfileValidationSupport;

/** RF08 critério 8: cada recurso da fachada /fhir/r4 passa no validador HAPI contra os perfis base R4. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Import(TestInfra.class)
class FhirValidationIT {

    static final Pattern ID = Pattern.compile("\"id\":\"([0-9a-f-]{36})\"");
    static final FhirContext CTX = FhirContext.forR4Cached();

    @Autowired MockMvc mvc;
    @Autowired MedicationCatalog catalog;

    private MockHttpServletResponse call(MockHttpServletRequestBuilder b, String token, String json) throws Exception {
        b.header("Authorization", "DE-Internal " + token);
        if (json != null) b.contentType(MediaType.APPLICATION_JSON).content(json);
        MockHttpServletResponse r = mvc.perform(b).andReturn().getResponse();
        r.setCharacterEncoding("UTF-8");
        return r;
    }

    private static String id(String body) {
        Matcher m = ID.matcher(body);
        assertThat(m.find()).as(body).isTrue();
        return m.group(1);
    }

    @Test
    void everyResourceValidatesAgainstTheBaseR4Profiles() throws Exception {
        Doctor d = Doctor.random("Dra. Ana Ribeiro");
        String w = TestTokens.token(d, true, true, "VERIFIED");
        String pid = id(call(post("/patients"), w, "{\"name\":\"Helena Duarte\",\"birthDate\":\"1964-03-02\",\"sex\":\"female\",\"localRecordNumber\":\"HC-4410\"}").getContentAsString());
        String created = call(post("/compositions"), w, "{\"patientId\":\"" + pid + "\",\"context\":\"TELECONSULTA\"}").getContentAsString();
        String cid = id(created);
        String encounterId = created.replaceAll(".*\"encounter\":\\{\"id\":\"([0-9a-f-]{36})\".*", "$1");
        call(put("/compositions/" + cid), w, """
                {"subjective":"Dispneia <3 dias> & tosse","objective":"SatO2 91%","assessment":{"text":"Pneumonia","icd10":["J18.9","I10"]},"plan":"Antibiótico"}""");
        assertThat(call(post("/compositions/" + cid + "/sign"), w, null).getStatus()).isEqualTo(200);

        String r = TestTokens.token(d, false, false, "VERIFIED");
        validate(r, List.of("/fhir/r4/Patient/" + pid, "/fhir/r4/Practitioner/" + d.id(), "/fhir/r4/Encounter/" + encounterId,
                "/fhir/r4/Composition/" + cid, "/fhir/r4/Composition?patient=" + pid, "/fhir/r4/Condition?encounter=" + encounterId,
                "/fhir/r4/Condition/" + cid + "-J189", "/fhir/r4/Provenance?target=Composition/" + cid, "/fhir/r4/metadata"));
        String comp = call(get("/fhir/r4/Composition/" + cid), r, null).getContentAsString();
        assertThat(comp).contains("\"status\":\"final\"").contains("61150-9").contains("Condition/" + cid + "-J189").contains("&lt;3 dias&gt; &amp; tosse");
        assertThat(call(get("/fhir/r4/Provenance?target=Composition/" + cid), r, null).getContentAsString()).contains("\"total\":1").contains("Author's Signature");
    }

    /** RF09: a receita e o pedido de exame assinados viram MedicationRequest e ServiceRequest válidos. */
    @Test
    void signedDocumentsValidateAsMedicationAndServiceRequests() throws Exception {
        Doctor d = Doctor.random("Dr. Bruno Teixeira");
        String w = TestTokens.token(d, true, false, "VERIFIED");
        String sudo = TestTokens.token(d, true, true, "VERIFIED");
        String pid = id(call(post("/patients"), w, "{\"name\":\"Marta Lopes\",\"birthDate\":\"1980-07-14\",\"sex\":\"female\"}").getContentAsString());
        String ggrem = catalog.search("amoxicilina 500 mg cap", 50).stream().filter(m -> m.antimicrobial() && !m.specialControl()).findFirst().orElseThrow().ggrem();

        String rx = id(call(post("/documents"), w, "{\"patientId\":\"" + pid + "\",\"kind\":\"RECEITA\"}").getContentAsString());
        String draft = call(put("/documents/" + rx), w, """
                {"medications":[{"ggrem":"%s","dose":"1","doseUnit":"cápsula","route":"ORAL","intervalHours":8,"durationDays":7,"quantity":"1 caixa"}]}"""
                .formatted(ggrem)).getContentAsString();
        assertThat(call(post("/documents/" + rx + "/sign"), sudo, "{\"reviewedHash\":\"" + hash(draft) + "\"}").getStatus()).isEqualTo(200);

        String ex = id(call(post("/documents"), w, "{\"patientId\":\"" + pid + "\",\"kind\":\"EXAME\"}").getContentAsString());
        draft = call(put("/documents/" + ex), w, "{\"exams\":[{\"loinc\":\"58410-2\"}],\"indication\":\"Febre há 5 dias\"}").getContentAsString();
        assertThat(call(post("/documents/" + ex + "/sign"), sudo, "{\"reviewedHash\":\"" + hash(draft) + "\"}").getStatus()).isEqualTo(200);

        String r = TestTokens.token(d, false, false, "VERIFIED");
        validate(r, List.of("/fhir/r4/MedicationRequest?patient=" + pid, "/fhir/r4/MedicationRequest/" + rx + "-m1",
                "/fhir/r4/ServiceRequest?patient=" + pid, "/fhir/r4/ServiceRequest/" + ex + "-e1"));
        assertThat(call(get("/fhir/r4/MedicationRequest/" + rx + "-m1"), r, null).getContentAsString()).contains(ggrem).contains("\"status\":\"active\"");
        assertThat(call(get("/fhir/r4/ServiceRequest/" + ex + "-e1"), r, null).getContentAsString()).contains("58410-2").contains("40304361");
        assertThat(call(get("/fhir/r4/MedicationRequest/" + rx + "-m9"), r, null).getStatus()).isEqualTo(404);
    }

    private static String hash(String body) {
        return body.replaceAll(".*\"contentHash\":\"([^\"]+)\".*", "$1");
    }

    private void validate(String token, List<String> paths) throws Exception {
        ValidationSupportChain chain = new ValidationSupportChain(new DefaultProfileValidationSupport(CTX), new SnapshotGeneratingValidationSupport(CTX),
                new InMemoryTerminologyServerValidationSupport(CTX), new CommonCodeSystemsTerminologyService(CTX));
        FhirValidator validator = CTX.newValidator().registerValidatorModule(new FhirInstanceValidator(chain));
        for (String path : paths) {
            MockHttpServletResponse res = call(get(path), token, null);
            assertThat(res.getStatus()).as(path + " " + res.getContentAsString()).isEqualTo(200);
            assertThat(res.getContentType()).startsWith("application/fhir+json");
            ValidationResult result = validator.validateWithResult(res.getContentAsString());
            var errors = result.getMessages().stream()
                    .filter(m -> m.getSeverity() == ResultSeverityEnum.ERROR || m.getSeverity() == ResultSeverityEnum.FATAL)
                    .map(m -> m.getLocationString() + ": " + m.getMessage()).toList();
            assertThat(errors).as(path).isEmpty();
        }
    }
}
