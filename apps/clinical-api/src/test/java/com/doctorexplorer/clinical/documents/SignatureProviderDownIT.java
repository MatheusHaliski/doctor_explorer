package com.doctorexplorer.clinical.documents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.doctorexplorer.clinical.support.TestInfra;
import com.doctorexplorer.clinical.support.TestTokens;
import com.doctorexplorer.clinical.support.TestTokens.Doctor;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

/** RF09 critério 7 · sem provedor (o padrão de produção até haver contrato): FALHA_ASSINATURA com causa, rascunho preservado. */
@SpringBootTest(properties = "doctor-explorer.signature.provider=unavailable")
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Import(TestInfra.class)
class SignatureProviderDownIT {

    @Autowired MockMvc mvc;

    private String call(MockHttpServletRequestBuilder b, String token, String json) throws Exception {
        b.header("Authorization", "DE-Internal " + token);
        if (json != null) b.contentType(MediaType.APPLICATION_JSON).content(json);
        MockHttpServletResponse r = mvc.perform(b).andReturn().getResponse();
        r.setCharacterEncoding("UTF-8");
        return r.getContentAsString();
    }

    private static String field(String name, String body) {
        Matcher m = Pattern.compile("\"" + name + "\":\"([^\"]+)\"").matcher(body);
        assertThat(m.find()).as(body).isTrue();
        return m.group(1);
    }

    @Test
    void failureIsReportedWithCauseAndTheDraftCanBeSignedAgainLater() throws Exception {
        Doctor d = Doctor.random("Dra. Ana Ribeiro");
        String w = TestTokens.token(d, true, true, "VERIFIED");
        String pid = field("id", call(post("/patients"), w, "{\"name\":\"Paciente Teste\",\"birthDate\":\"1980-01-01\"}"));
        String id = field("id", call(post("/documents"), w, "{\"patientId\":\"" + pid + "\",\"kind\":\"EXAME\"}"));
        String draft = call(put("/documents/" + id), w, "{\"exams\":[{\"loinc\":\"2345-7\"}],\"indication\":\"Rastreio\"}");
        String failed = call(post("/documents/" + id + "/sign"), w, "{\"reviewedHash\":\"" + field("contentHash", draft) + "\"}");
        assertThat(failed).contains("\"state\":\"FALHA_ASSINATURA\"").contains("Nenhum provedor de assinatura ICP-Brasil").contains("2345-7");
        // continua editável e assinável numa nova tentativa
        assertThat(call(put("/documents/" + id), w, "{\"exams\":[{\"loinc\":\"2345-7\"}],\"indication\":\"Rastreio anual\"}")).contains("\"state\":\"RASCUNHO\"");
    }
}
