package com.doctorexplorer.core.community;

import static org.assertj.core.api.Assertions.assertThat;

import com.doctorexplorer.core.identity.service.LoggingMailer;
import com.doctorexplorer.core.support.ApiClient;
import com.doctorexplorer.core.support.TestInfra;
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

/** RF04 · grupos por RQE, publicação anonimizada, pareceres e feed segmentado por classificações. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Import(TestInfra.class)
class CommunityIT {

    static final String PASSWORD = "frase longa e segura 2026";

    @Autowired MockMvc mvc;
    @Autowired LoggingMailer mailer;
    @Autowired StringRedisTemplate redis;

    @BeforeEach
    void setUp() {
        mailer.clear();
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    /** Médico verificado com RQE de UTI confirmado (prefixo 12 na fonte simulada). */
    private ApiClient verifiedIntensivist() throws Exception {
        ApiClient c = new ApiClient(mvc);
        c.register("uti+" + System.nanoTime() + "@example.org", PASSWORD);
        c.activateEmail(mailer);
        String crm = "12" + String.format("%04d", System.nanoTime() % 10_000);
        c.post("/api/v1/credentials", "{\"crmNumber\":\"" + crm + "\",\"crmUf\":\"PR\",\"specialties\":[{\"specialtyCode\":\"UTI\",\"rqe\":\"11111\"}]}");
        return c;
    }

    @Test
    void unverifiedAccountsCannotReadTheFeed() throws Exception {
        ApiClient c = new ApiClient(mvc);
        c.register("semselo+" + System.nanoTime() + "@example.org", PASSWORD);
        c.activateEmail(mailer);
        MockHttpServletResponse res = c.get("/api/v1/feed");
        assertThat(res.getStatus()).isEqualTo(403);
        assertThat(res.getContentAsString()).contains("selo-necessario");
    }

    @Test
    void rqeJoinsAutomaticallyAndOthersWaitForApproval() throws Exception {
        ApiClient c = verifiedIntensivist();
        assertThat(c.post("/api/v1/groups/uti/join", null).getContentAsString()).contains("\"state\":\"ACTIVE\"");
        assertThat(c.post("/api/v1/groups/derm/join", null).getContentAsString()).contains("\"state\":\"REQUESTED\"");
        assertThat(c.post("/api/v1/groups/xyz/join", null).getStatus()).isEqualTo(404);
        String groups = c.get("/api/v1/groups").getContentAsString();
        assertThat(groups).contains("\"specialtyCode\":\"UTI\"").contains("\"membership\":\"ACTIVE\"").contains("\"membership\":\"REQUESTED\"");
    }

    @Test
    void identifiersBlockPublicationWithAllExcerpts() throws Exception {
        ApiClient c = verifiedIntensivist();
        c.post("/api/v1/groups/uti/join", null);
        MockHttpServletResponse res = c.post("/api/v1/posts", """
                {"specialtyCode":"UTI","kind":"CASE","title":"Choque séptico no paciente João da Silva",
                 "complaint":"CPF 123.456.789-09, internado em 12/03/2024","question":"Corticoide?"}
                """);
        assertThat(res.getStatus()).isEqualTo(422);
        String body = res.getContentAsString();
        assertThat(body).contains("NOME_PACIENTE").contains("CPF").contains("DATA_COMPLETA").contains("Há 3 campos para corrigir.");
    }

    @Test
    void publishCommentResolveAndSegmentTheFeed() throws Exception {
        ApiClient ana = verifiedIntensivist();
        ana.post("/api/v1/groups/uti/join", null);
        MockHttpServletResponse created = ana.post("/api/v1/posts", """
                {"specialtyCode":"UTI","kind":"CASE","title":"Choque séptico refratário, 60 anos, sem foco definido",
                 "complaint":"Vasopressor em dose alta há 36 h, lactato em queda lenta.","question":"Alguém usaria corticoide neste ponto?","tags":["#sepse","vasopressor"]}
                """);
        assertThat(created.getStatus()).isEqualTo(201);
        assertThat(created.getContentAsString()).contains("\"state\":\"SEM_RESPOSTA\"").contains("\"origin\":\"grupo\"").contains("\"tags\":[\"sepse\",\"vasopressor\"]");
        String postId = group(Pattern.compile("\"id\":\"([0-9a-f-]{36})\""), created.getContentAsString());

        MockHttpServletResponse network = ana.post("/api/v1/posts", """
                {"specialtyCode":"UTI","kind":"EDUCATION","audience":"NETWORK","title":"Caso da semana: desmame ventilatório","question":"Três perguntas guiadas."}
                """);
        assertThat(network.getStatus()).isEqualTo(201);

        // outro intensivista (membro do grupo) dá parecer: estado vira EM_DISCUSSAO
        ApiClient bruno = verifiedIntensivist();
        bruno.post("/api/v1/groups/uti/join", null);
        assertThat(bruno.post("/api/v1/posts/" + postId + "/comments", "{\"body\":\"Eco à beira do leito antes de decidir.\",\"opinion\":true}").getStatus()).isEqualTo(201);
        assertThat(bruno.get("/api/v1/posts/" + postId).getContentAsString()).contains("\"state\":\"EM_DISCUSSAO\"").contains("\"opinions\":1").contains("\"canRecordInEhr\":false");
        assertThat(ana.get("/api/v1/posts/" + postId).getContentAsString()).contains("\"canRecordInEhr\":true");

        // segmentação por eixos
        assertThat(ana.get("/api/v1/feed").getContentAsString()).contains("\"count\":2");
        assertThat(ana.get("/api/v1/feed?kind=CASE").getContentAsString()).contains("\"count\":1").contains("Choque séptico");
        assertThat(ana.get("/api/v1/feed?origin=rede").getContentAsString()).contains("\"count\":1").contains("Caso da semana");
        assertThat(ana.get("/api/v1/feed?state=EM_DISCUSSAO").getContentAsString()).contains("\"count\":1").contains("Choque séptico");
        assertThat(ana.get("/api/v1/feed?unanswered=true").getContentAsString()).contains("\"count\":1").contains("Caso da semana");
        assertThat(ana.get("/api/v1/feed?specialty=DERM").getContentAsString()).contains("\"count\":0");

        // quem não é do grupo vê só a rede
        ApiClient carla = new ApiClient(mvc);
        carla.register("derm+" + System.nanoTime() + "@example.org", PASSWORD);
        carla.activateEmail(mailer);
        carla.post("/api/v1/credentials", "{\"crmNumber\":\"12" + String.format("%04d", System.nanoTime() % 10_000) + "\",\"crmUf\":\"RS\",\"specialties\":[]}");
        assertThat(carla.get("/api/v1/feed").getContentAsString()).contains("\"count\":1").doesNotContain("Choque séptico");
        assertThat(carla.get("/api/v1/posts/" + postId).getStatus()).isEqualTo(404);

        // resolver: só o autor; depois disso parecer novo é recusado
        assertThat(bruno.post("/api/v1/posts/" + postId + "/resolve", null).getStatus()).isEqualTo(404);
        assertThat(ana.post("/api/v1/posts/" + postId + "/resolve", null).getContentAsString()).contains("\"state\":\"RESOLVIDO\"");
        assertThat(bruno.post("/api/v1/posts/" + postId + "/comments", "{\"body\":\"Mais um parecer\",\"opinion\":true}").getStatus()).isEqualTo(409);
        assertThat(ana.get("/api/v1/feed?state=RESOLVIDO").getContentAsString()).contains("\"count\":1");
    }

    @Test
    void institutionalKindsAreNotPublishedByIndividuals() throws Exception {
        ApiClient c = verifiedIntensivist();
        c.post("/api/v1/groups/uti/join", null);
        MockHttpServletResponse res = c.post("/api/v1/posts", "{\"specialtyCode\":\"UTI\",\"kind\":\"PROTOCOL\",\"title\":\"Protocolo de TEV v3\"}");
        assertThat(res.getStatus()).isEqualTo(422);
        assertThat(res.getContentAsString()).contains("publicados pela instituição");
    }

    private static String group(Pattern p, String body) {
        Matcher m = p.matcher(body);
        assertThat(m.find()).isTrue();
        return m.group(1);
    }
}
