package com.doctorexplorer.core.credentialing;

import static org.assertj.core.api.Assertions.assertThat;

import com.doctorexplorer.core.credentialing.service.CredentialingService;
import com.doctorexplorer.core.identity.service.LoggingMailer;
import com.doctorexplorer.core.support.ApiClient;
import com.doctorexplorer.core.support.TestCrm;
import com.doctorexplorer.core.support.TestInfra;
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

/** RF02 · CRM/RQE contra a fonte simulada do CFM (FakeCfmRegistry, perfil it). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Import(TestInfra.class)
class CredentialingIT {

    @Autowired MockMvc mvc;
    @Autowired LoggingMailer mailer;
    @Autowired StringRedisTemplate redis;
    @Autowired CredentialingService credentialing;

    ApiClient api;

    @BeforeEach
    void setUp() {
        api = new ApiClient(mvc);
        mailer.clear();
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    /** CRM único por teste; o prefixo decide a resposta da fonte simulada (ver FakeCfmRegistry). */
    static String crm(String prefix) {
        return TestCrm.next(prefix);
    }

    @Test
    void verifiesCrmAndRqeAgainstTheRegistry() throws Exception {
        api.register("ana+" + System.nanoTime() + "@example.org", "frase longa e segura 2026"); // nome "Dra. Ana Ribeiro"
        api.activateEmail(mailer);
        assertThat(api.get("/api/v1/credentials").getContentAsString()).contains("\"seal\":\"NONE\"");

        MockHttpServletResponse res = api.post("/api/v1/credentials",
                "{\"crmNumber\":\"" + crm("12") + "\",\"crmUf\":\"pr\",\"specialties\":[{\"specialtyCode\":\"UTI\",\"rqe\":\"11111\"},{\"specialtyCode\":\"DERM\",\"rqe\":\"99999\"}]}");
        assertThat(res.getStatus()).isEqualTo(202);
        String body = res.getContentAsString();
        assertThat(body).contains("\"seal\":\"VERIFIED\"").contains("\"crmUf\":\"PR\"")
                .contains("\"confirmed\":true")
                .contains("\"confirmed\":false")
                .contains("1 especialidade(s) sem RQE correspondente");
    }

    @Test
    void rejectsWhenNameDoesNotMatchOrSituationIsIrregular() throws Exception {
        api.register("outro+" + System.nanoTime() + "@example.org", "frase longa e segura 2026"); // conta "Dra. Ana Ribeiro"
        api.activateEmail(mailer);
        MockHttpServletResponse bruno = api.post("/api/v1/credentials", "{\"crmNumber\":\"" + crm("65") + "\",\"crmUf\":\"SP\",\"specialties\":[]}");
        assertThat(bruno.getContentAsString()).contains("\"seal\":\"REJECTED\"").contains("nome da conta não confere");

        MockHttpServletResponse suspenso = api.post("/api/v1/credentials", "{\"crmNumber\":\"" + crm("99") + "\",\"crmUf\":\"MG\",\"specialties\":[]}");
        assertThat(suspenso.getContentAsString()).contains("\"seal\":\"REJECTED\"").contains("Situação no CFM: Suspenso");

        MockHttpServletResponse naoExiste = api.post("/api/v1/credentials", "{\"crmNumber\":\"" + crm("42") + "\",\"crmUf\":\"RS\",\"specialties\":[]}");
        assertThat(naoExiste.getContentAsString()).contains("\"seal\":\"REJECTED\"").contains("não encontrado na fonte do CFM");
    }

    @Test
    void staysPendingWhenRegistryIsUnavailable() throws Exception {
        api.register("fora+" + System.nanoTime() + "@example.org", "frase longa e segura 2026");
        api.activateEmail(mailer);
        MockHttpServletResponse res = api.post("/api/v1/credentials", "{\"crmNumber\":\"" + crm("00") + "\",\"crmUf\":\"PR\",\"specialties\":[]}");
        assertThat(res.getStatus()).isEqualTo(202);
        assertThat(res.getContentAsString()).contains("\"seal\":\"PENDING\"").contains("\"outcome\":\"UNAVAILABLE\"");
    }

    @Test
    void validatesInputAllAtOnce() throws Exception {
        api.register("val+" + System.nanoTime() + "@example.org", "frase longa e segura 2026");
        api.activateEmail(mailer);
        MockHttpServletResponse res = api.post("/api/v1/credentials", "{\"crmNumber\":\"12\",\"crmUf\":\"Paraná\",\"specialties\":[{\"specialtyCode\":\"XYZ\",\"rqe\":\"1\"}]}");
        assertThat(res.getStatus()).isEqualTo(422);
        assertThat(res.getContentAsString()).contains("Há 4 campos para corrigir.").contains("Especialidade desconhecida").contains("UF do CRM");
    }

    @Test
    void sameCrmCannotBeVerifiedTwice() throws Exception {
        String crm = crm("12");
        api.register("primeira+" + System.nanoTime() + "@example.org", "frase longa e segura 2026");
        api.activateEmail(mailer);
        assertThat(api.post("/api/v1/credentials", "{\"crmNumber\":\"" + crm + "\",\"crmUf\":\"PR\",\"specialties\":[]}").getContentAsString()).contains("\"seal\":\"VERIFIED\"");

        ApiClient segunda = new ApiClient(mvc);
        segunda.register("segunda+" + System.nanoTime() + "@example.org", "frase longa e segura 2026");
        segunda.activateEmail(mailer);
        MockHttpServletResponse res = segunda.post("/api/v1/credentials", "{\"crmNumber\":\"" + crm + "\",\"crmUf\":\"PR\",\"specialties\":[]}");
        assertThat(res.getStatus()).isEqualTo(422);
        assertThat(res.getContentAsString()).contains("já está verificado em outra conta");
    }

    @Test
    void credentialsRequireASession() throws Exception {
        assertThat(new ApiClient(mvc).get("/api/v1/credentials").getStatus()).isEqualTo(401);
    }
}
