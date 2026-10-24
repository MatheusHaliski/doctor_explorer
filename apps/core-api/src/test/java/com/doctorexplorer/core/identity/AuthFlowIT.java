package com.doctorexplorer.core.identity;

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

/** RF01 · cadastro → código por e-mail → ativação → logout → login, com sessão real no Valkey e CSRF. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Import(TestInfra.class)
class AuthFlowIT {

    @Autowired MockMvc mvc;
    @Autowired LoggingMailer mailer;
    @Autowired StringRedisTemplate redis;

    ApiClient api;

    @BeforeEach
    void setUp() {
        api = new ApiClient(mvc);
        mailer.clear();
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    @Test
    void registerVerifyLogoutAndLoginAgain() throws Exception {
        String email = "ana+" + System.nanoTime() + "@example.org";
        String password = "frase longa e segura 2026";

        MockHttpServletResponse created = api.register(email, password);
        assertThat(created.getStatus()).isEqualTo(201);
        assertThat(created.getContentAsString()).contains("\"next\":\"verify-email\"").contains("PENDING_VERIFICATION");
        assertThat(api.hasSessionCookie()).isTrue();
        String sessionCookie = created.getHeaders("Set-Cookie").stream().filter(h -> h.startsWith("de_session=")).findFirst().orElseThrow();
        assertThat(sessionCookie).contains("HttpOnly").contains("SameSite=Lax").contains("Path=/").doesNotContain("Max-Age");

        MockHttpServletResponse me = api.get("/api/v1/me");
        assertThat(me.getStatus()).isEqualTo(200);
        assertThat(me.getContentAsString()).contains("\"emailVerified\":false");

        String code = lastCode();
        MockHttpServletResponse wrong = api.post("/api/v1/me/verify-email", "{\"code\":\"000000\"}");
        assertThat(wrong.getStatus()).isEqualTo(422);
        assertThat(wrong.getContentAsString()).contains("Código incorreto").contains("Restam 4 tentativa(s)");

        MockHttpServletResponse ok = api.post("/api/v1/me/verify-email", "{\"code\":\"" + code + "\"}");
        assertThat(ok.getStatus()).isEqualTo(200);
        assertThat(ok.getContentAsString()).contains("ACTIVE");

        assertThat(api.post("/api/v1/auth/logout", null).getStatus()).isEqualTo(204);
        assertThat(api.get("/api/v1/me").getStatus()).isEqualTo(401);

        MockHttpServletResponse bad = api.login(email, "senha errada mesmo");
        assertThat(bad.getStatus()).isEqualTo(401);
        assertThat(bad.getContentAsString()).contains("E-mail ou senha incorretos.");

        MockHttpServletResponse login = api.login(email, password);
        assertThat(login.getStatus()).isEqualTo(200);
        assertThat(login.getContentAsString()).contains("\"status\":\"ACTIVE\"");
        assertThat(api.get("/api/v1/auth/session").getContentAsString()).contains("Dra. Ana Ribeiro");
    }

    @Test
    void registerReturnsAllProblemsAtOnce() throws Exception {
        MockHttpServletResponse res = api.post("/api/v1/auth/register", """
                {"email":"nao-e-email","displayName":"A","birthDate":"2015-01-01","password":"ana123",
                 "acceptTerms":false,"acceptPrivacy":false,"termsVersion":"2026-10-24","privacyVersion":"2026-10-24"}
                """);
        assertThat(res.getStatus()).isEqualTo(422);
        String body = res.getContentAsString();
        assertThat(body).contains("Informe um e-mail válido.")
                .contains("O nome precisa ter de 2 a 120 caracteres.")
                .contains("18 anos ou mais")
                .contains("pelo menos 12 caracteres")
                .contains("aceitar os Termos de Uso")
                .contains("aceitar a Política de Privacidade");
    }

    @Test
    void writesWithoutCsrfHeaderAreRefused() throws Exception {
        MockHttpServletResponse res = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .post("/api/v1/auth/login").contentType("application/json").content("{}")).andReturn().getResponse();
        assertThat(res.getStatus()).isEqualTo(403);
    }

    @Test
    void loginIsRateLimitedPerAccount() throws Exception {
        String email = "limite+" + System.nanoTime() + "@example.org";
        api.register(email, "frase longa e segura 2026");
        api.forgetSession();
        for (int i = 0; i < 10; i++) {
            assertThat(api.login(email, "errada " + i).getStatus()).isEqualTo(401);
        }
        MockHttpServletResponse locked = api.login(email, "frase longa e segura 2026");
        assertThat(locked.getStatus()).isEqualTo(429);
        assertThat(locked.getHeader("Retry-After")).isNotBlank();
        assertThat(locked.getContentAsString()).contains("Muitas tentativas");
    }

    @Test
    void resendIsLimitedToOncePerMinute() throws Exception {
        api.register("reenvio+" + System.nanoTime() + "@example.org", "frase longa e segura 2026");
        MockHttpServletResponse res = api.post("/api/v1/me/verify-email/resend", null);
        assertThat(res.getStatus()).isEqualTo(429);
        assertThat(res.getHeader("Retry-After")).isNotBlank();
    }

    private String lastCode() {
        var sent = mailer.outbox();
        Matcher m = Pattern.compile("Seu código é (\\d{6})").matcher(sent.get(sent.size() - 1).body());
        assertThat(m.find()).isTrue();
        return m.group(1);
    }
}
