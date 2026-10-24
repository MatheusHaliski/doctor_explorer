package com.doctorexplorer.core.institution;

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

/** RF11/RF12/RF03 · diretório com CNPJ+CNES, hierarquia, vínculos, papéis na árvore, protocolos no feed e perfil. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Import(TestInfra.class)
class DirectoryIT {

    static final String PASSWORD = "frase longa e segura 2026";
    static final Pattern ID = Pattern.compile("\"id\":\"([0-9a-f-]{36})\"");

    @Autowired MockMvc mvc;
    @Autowired LoggingMailer mailer;
    @Autowired StringRedisTemplate redis;

    @BeforeEach
    void setUp() {
        mailer.clear();
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    private ApiClient verifiedDoctor(String tag) throws Exception {
        ApiClient c = new ApiClient(mvc);
        c.register(tag + "+" + System.nanoTime() + "@example.org", PASSWORD);
        c.activateEmail(mailer);
        c.post("/api/v1/credentials", "{\"crmNumber\":\"12" + String.format("%04d", System.nanoTime() % 10_000) + "\",\"crmUf\":\"PR\",\"specialties\":[{\"specialtyCode\":\"UTI\",\"rqe\":\"11111\"}]}");
        return c;
    }

    /** CNPJ 12… (ATIVA) + CNES 12… cujo CNPJ simulado é "12" + 12 dígitos do CNES: coerente. */
    private static String[] coherentCnpjCnes() {
        String suffix = String.format("%05d", System.nanoTime() % 100_000);
        String cnes = "12" + suffix;                       // 7 dígitos
        String cnpj = "12" + (suffix + "000000000000").substring(0, 12); // 14 dígitos
        return new String[] {cnpj, cnes};
    }

    private String registerHospital(ApiClient gestor, String name, Double lat, Double lng) throws Exception {
        String[] ids = coherentCnpjCnes();
        MockHttpServletResponse res = gestor.post("/api/v1/institutions", """
                {"legalName":"%s LTDA","tradeName":"%s","cnpj":"%s","cnes":"%s","type":"HOSPITAL","city":"Curitiba","uf":"pr","lat":%s,"lng":%s}
                """.formatted(name, name, ids[0], ids[1], lat, lng));
        assertThat(res.getStatus()).as(res.getContentAsString()).isEqualTo(201);
        assertThat(res.getContentAsString()).contains("\"verified\":true").contains("CNES confere com o CNPJ");
        return group(ID, res.getContentAsString());
    }

    @Test
    void registersWithCnpjAndCnesAndRejectsIncoherentOnes() throws Exception {
        ApiClient gestor = verifiedDoctor("gestor");
        registerHospital(gestor, "Hospital Santa Clara", -25.43, -49.27);

        MockHttpServletResponse wrongCnes = gestor.post("/api/v1/institutions",
                "{\"legalName\":\"X LTDA\",\"tradeName\":\"X\",\"cnpj\":\"12" + String.format("%012d", System.nanoTime() % 1_000_000_000_000L) + "\",\"cnes\":\"9900001\",\"type\":\"HOSPITAL\",\"city\":\"Curitiba\",\"uf\":\"PR\"}");
        assertThat(wrongCnes.getStatus()).isEqualTo(202);
        assertThat(wrongCnes.getContentAsString()).contains("\"state\":\"REJEITADA\"").contains("pertence a outro CNPJ");

        MockHttpServletResponse invalid = gestor.post("/api/v1/institutions", "{\"legalName\":\"\",\"tradeName\":\"\",\"cnpj\":\"12\",\"type\":\"HOSPITAL\",\"city\":\"\",\"uf\":\"Paraná\"}");
        assertThat(invalid.getStatus()).isEqualTo(422);
        assertThat(invalid.getContentAsString()).contains("CNPJ com 14 dígitos").contains("CNES com 7 dígitos").contains("razão social");
    }

    @Test
    void publicDirectorySearchAndRadiusOnlyWithLocationConsent() throws Exception {
        ApiClient gestor = verifiedDoctor("gestor");
        String near = registerHospital(gestor, "Hospital Perto Curitiba", -25.43, -49.27);
        registerHospital(gestor, "Hospital Longe Londrina", -23.31, -51.16);

        ApiClient visitante = new ApiClient(mvc);
        String all = visitante.get("/api/v1/institutions?q=Hospital&uf=PR").getContentAsString();
        assertThat(all).contains("Hospital Perto Curitiba").contains("Hospital Longe Londrina");
        assertThat(visitante.get("/api/v1/institutions/" + near).getStatus()).isEqualTo(200);

        // raio sem consentimento LOCATION: lat/lng ignorados
        String noConsent = gestor.get("/api/v1/institutions?lat=-25.43&lng=-49.27&radiusKm=50").getContentAsString();
        assertThat(noConsent).contains("\"locationUsed\":false").contains("Hospital Longe Londrina");
        gestor.put("/api/v1/privacy/consents/location", "{\"granted\":true}");
        String withConsent = gestor.get("/api/v1/institutions?lat=-25.43&lng=-49.27&radiusKm=50").getContentAsString();
        assertThat(withConsent).contains("\"locationUsed\":true").contains("Hospital Perto Curitiba").doesNotContain("Hospital Longe Londrina").contains("distanceKm");
    }

    @Test
    void membershipNeedsBothSidesAndRolesInheritDownTheTree() throws Exception {
        ApiClient gestor = verifiedDoctor("gestor");
        String hospital = registerHospital(gestor, "Hospital Santa Clara", -25.43, -49.27);
        // unidade filha criada pelo gestor
        String[] ids = coherentCnpjCnes();
        MockHttpServletResponse unit = gestor.post("/api/v1/institutions", """
                {"legalName":"UTI Adulto LTDA","tradeName":"UTI Adulto Santa Clara","cnpj":"%s","cnes":"%s","type":"UNIT","parentId":"%s","city":"Curitiba","uf":"PR"}
                """.formatted(ids[0], ids[1], hospital));
        assertThat(unit.getStatus()).isEqualTo(201);
        String unitId = group(ID, unit.getContentAsString());

        ApiClient medico = verifiedDoctor("medico");
        String medicoId = group(Pattern.compile("\"id\":\"([0-9a-f-]{36})\""), medico.get("/api/v1/me").getContentAsString());
        assertThat(medico.post("/api/v1/institutions/" + unitId + "/join", null).getContentAsString()).contains("\"state\":\"REQUESTED\"");
        assertThat(gestor.get("/api/v1/institutions/" + unitId + "/staff").getContentAsString()).doesNotContain(medicoId); // ainda não aceito

        // gestor do hospital (ancestral) aprova na unidade: herança para baixo
        assertThat(gestor.post("/api/v1/institutions/" + unitId + "/members/" + medicoId, null).getContentAsString()).contains("\"state\":\"ACTIVE\"");
        assertThat(gestor.get("/api/v1/institutions/" + unitId + "/staff").getContentAsString()).contains(medicoId);

        // médico comum não gere membros (403 com motivo); outra pessoa de fora recebe 404 (não revela)
        ApiClient outro = verifiedDoctor("outro");
        assertThat(medico.post("/api/v1/institutions/" + unitId + "/members/" + medicoId, null).getStatus()).isEqualTo(403);
        assertThat(outro.get("/api/v1/institutions/" + unitId + "/members").getStatus()).isEqualTo(404);

        // papel nunca acima do de quem concede
        assertThat(gestor.put("/api/v1/institutions/" + unitId + "/members/" + medicoId + "/role", "{\"role\":\"UNIT_MANAGER\"}").getContentAsString()).contains("UNIT_MANAGER");
        assertThat(gestor.put("/api/v1/institutions/" + unitId + "/members/" + medicoId + "/role", "{\"role\":\"NETWORK_ADMIN\"}").getStatus()).isEqualTo(403);
        assertThat(medico.get("/api/v1/institutions/" + unitId).getContentAsString()).contains("\"myRole\":\"UNIT_MANAGER\"");
        assertThat(medico.get("/api/v1/institutions/" + hospital).getContentAsString()).contains("\"myRole\":\"NONE\""); // não herda para cima
    }

    @Test
    void institutionalProtocolsAppearInMembersFeedAsInstitutionOrigin() throws Exception {
        ApiClient gestor = verifiedDoctor("gestor");
        String hospital = registerHospital(gestor, "Hospital Santa Clara", null, null);
        ApiClient medico = verifiedDoctor("medico");
        String medicoId = group(ID, medico.get("/api/v1/me").getContentAsString());
        medico.post("/api/v1/institutions/" + hospital + "/join", null);
        gestor.post("/api/v1/institutions/" + hospital + "/members/" + medicoId, null);

        MockHttpServletResponse proto = gestor.post("/api/v1/institutions/" + hospital + "/posts",
                "{\"kind\":\"PROTOCOL\",\"specialtyCode\":\"CLIN\",\"title\":\"Protocolo de profilaxia de TEV v3\",\"body\":\"Escore de Pádua na admissão e revisão em 72 h.\"}");
        assertThat(proto.getStatus()).isEqualTo(201);
        assertThat(proto.getContentAsString()).contains("\"origin\":\"instituicao\"").contains("\"state\":\"RESOLVIDO\"");
        assertThat(medico.post("/api/v1/institutions/" + hospital + "/posts", "{\"kind\":\"NOTICE\",\"title\":\"Comunicado qualquer\",\"body\":\"x\"}").getStatus()).isEqualTo(403);

        assertThat(medico.get("/api/v1/feed?origin=instituicao").getContentAsString()).contains("Protocolo de profilaxia");
        ApiClient fora = verifiedDoctor("fora");
        assertThat(fora.get("/api/v1/feed?origin=instituicao").getContentAsString()).contains("\"count\":0");
    }

    @Test
    void profileVisibilityAndAdvertisingRules() throws Exception {
        ApiClient ana = verifiedDoctor("ana");
        String anaId = group(ID, ana.get("/api/v1/me").getContentAsString());
        ApiClient visitante = new ApiClient(mvc);
        assertThat(visitante.get("/api/v1/profiles/" + anaId).getStatus()).isEqualTo(404); // padrão: rede verificada

        MockHttpServletResponse ad = ana.put("/api/v1/me/profile", "{\"bio\":\"Resultado garantido, a melhor intensivista, consulta R$ 99\"}");
        assertThat(ad.getStatus()).isEqualTo(422);
        assertThat(ad.getContentAsString()).contains("Não prometa resultado").contains("superlativos").contains("Preço");

        assertThat(ana.put("/api/v1/me/profile", "{\"bio\":\"Intensivista, UTI adulto.\",\"visibility\":\"PUBLIC_MINIMAL\"}").getStatus()).isEqualTo(200);
        String publicView = visitante.get("/api/v1/profiles/" + anaId).getContentAsString();
        assertThat(publicView).contains("Dra. Ana Ribeiro").contains("\"seal\":\"VERIFIED\"").contains("CRM-PR").doesNotContain("bio");
        ApiClient colega = verifiedDoctor("colega");
        assertThat(colega.get("/api/v1/profiles/" + anaId).getContentAsString()).contains("Intensivista, UTI adulto.");

        MockHttpServletResponse renamed = ana.put("/api/v1/me/profile", "{\"displayName\":\"Dra. Ana R. Silva\"}");
        assertThat(renamed.getContentAsString()).contains("\"seal\":\"PENDING\"").contains("voltou para análise");
    }

    private static String group(Pattern p, String body) {
        Matcher m = p.matcher(body);
        assertThat(m.find()).as(body).isTrue();
        return m.group(1);
    }
}
