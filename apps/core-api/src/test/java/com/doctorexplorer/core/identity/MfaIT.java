package com.doctorexplorer.core.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.doctorexplorer.core.identity.service.LoggingMailer;
import com.doctorexplorer.core.identity.service.Totp;
import com.doctorexplorer.core.support.ApiClient;
import com.doctorexplorer.core.support.TestInfra;
import java.time.Instant;
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

/** RF01 · 2FA, modo sudo, troca de senha e sessões, com sessão real no Valkey. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it")
@Import(TestInfra.class)
class MfaIT {

    static final String PASSWORD = "frase longa e segura 2026";
    static final Pattern SECRET = Pattern.compile("\"secret\":\"([A-Z2-7]+)\"");
    static final Pattern RECOVERY = Pattern.compile("\"recoveryCodes\":\\[\"([a-z0-9-]+)\"");

    @Autowired MockMvc mvc;
    @Autowired LoggingMailer mailer;
    @Autowired StringRedisTemplate redis;

    ApiClient api;
    String email;

    @BeforeEach
    void setUp() throws Exception {
        api = new ApiClient(mvc);
        mailer.clear();
        redis.getConnectionFactory().getConnection().serverCommands().flushDb();
        email = "mfa+" + System.nanoTime() + "@example.org";
        api.register(email, PASSWORD);
    }

    @Test
    void sensitiveActionsRequireSudo() throws Exception {
        MockHttpServletResponse res = api.post("/api/v1/me/mfa/totp/setup", null);
        assertThat(res.getStatus()).isEqualTo(403);
        assertThat(res.getContentAsString()).contains("sudo-necessario").contains("/api/v1/me/sudo");

        assertThat(api.post("/api/v1/me/sudo", "{\"password\":\"errada\"}").getStatus()).isEqualTo(401);
        assertThat(api.post("/api/v1/me/sudo", "{\"password\":\"" + PASSWORD + "\"}").getStatus()).isEqualTo(200);
        assertThat(api.post("/api/v1/me/mfa/totp/setup", null).getStatus()).isEqualTo(200);
    }

    @Test
    void enableTotpThenLoginWithCodeAndWithRecoveryCode() throws Exception {
        api.post("/api/v1/me/sudo", "{\"password\":\"" + PASSWORD + "\"}");
        String setup = api.post("/api/v1/me/mfa/totp/setup", null).getContentAsString();
        assertThat(setup).contains("otpauth://totp/Doctor%20Explorer:");
        byte[] secret = base32Decode(group(SECRET, setup));

        assertThat(api.post("/api/v1/me/mfa/totp/confirm", "{\"code\":\"000000\"}").getStatus()).isEqualTo(422);
        MockHttpServletResponse confirmed = api.post("/api/v1/me/mfa/totp/confirm", "{\"code\":\"" + now(secret) + "\"}");
        assertThat(confirmed.getStatus()).isEqualTo(200);
        String recovery = group(RECOVERY, confirmed.getContentAsString());
        assertThat(api.get("/api/v1/me/mfa").getContentAsString()).contains("\"enabled\":true").contains("\"recoveryCodesLeft\":10");

        // login em etapas: senha → 2fa
        api.post("/api/v1/auth/logout", null);
        MockHttpServletResponse step1 = api.login(email, PASSWORD);
        assertThat(step1.getStatus()).isEqualTo(200);
        assertThat(step1.getContentAsString()).isEqualTo("{\"next\":\"2fa\"}");
        assertThat(api.get("/api/v1/me").getStatus()).isEqualTo(401); // etapa intermediária não autentica

        assertThat(api.post("/api/v1/auth/login/2fa", "{\"code\":\"123456\"}").getStatus()).isEqualTo(401);
        MockHttpServletResponse step2 = api.post("/api/v1/auth/login/2fa", "{\"code\":\"" + now(secret) + "\"}");
        assertThat(step2.getStatus()).isEqualTo(200);
        assertThat(api.get("/api/v1/me").getStatus()).isEqualTo(200);

        // código de recuperação: entra uma vez, depois não vale mais
        api.post("/api/v1/auth/logout", null);
        api.login(email, PASSWORD);
        assertThat(api.post("/api/v1/auth/login/2fa", "{\"code\":\"" + recovery + "\"}").getStatus()).isEqualTo(200);
        assertThat(api.get("/api/v1/me/mfa").getContentAsString()).contains("\"recoveryCodesLeft\":9");
        api.post("/api/v1/auth/logout", null);
        api.login(email, PASSWORD);
        assertThat(api.post("/api/v1/auth/login/2fa", "{\"code\":\"" + recovery + "\"}").getStatus()).isEqualTo(401);

        // 2fa sem etapa pendente
        ApiClient fresh = new ApiClient(mvc);
        assertThat(fresh.post("/api/v1/auth/login/2fa", "{\"code\":\"123456\"}").getStatus()).isEqualTo(409);
    }

    @Test
    void fiveWrongMfaCodesLockTheStep() throws Exception {
        api.post("/api/v1/me/sudo", "{\"password\":\"" + PASSWORD + "\"}");
        byte[] secret = base32Decode(group(SECRET, api.post("/api/v1/me/mfa/totp/setup", null).getContentAsString()));
        api.post("/api/v1/me/mfa/totp/confirm", "{\"code\":\"" + now(secret) + "\"}");
        api.post("/api/v1/auth/logout", null);
        api.login(email, PASSWORD);
        for (int i = 0; i < 5; i++) assertThat(api.post("/api/v1/auth/login/2fa", "{\"code\":\"000000\"}").getStatus()).isEqualTo(401);
        MockHttpServletResponse locked = api.post("/api/v1/auth/login/2fa", "{\"code\":\"" + now(secret) + "\"}");
        assertThat(locked.getStatus()).isEqualTo(429);
        assertThat(locked.getHeader("Retry-After")).isNotBlank();
    }

    @Test
    void changePasswordEndsOtherSessionsAndListsSessionsWithoutRealIds() throws Exception {
        ApiClient phone = new ApiClient(mvc);
        phone.login(email, PASSWORD);
        String list = api.get("/api/v1/me/sessions").getContentAsString();
        assertThat(list).contains("\"current\":true").contains("\"current\":false").contains("ApiClient/test");
        assertThat(list).doesNotContain("=="); // ids reais (base64) nunca aparecem

        api.post("/api/v1/me/sudo", "{\"password\":\"" + PASSWORD + "\"}");
        MockHttpServletResponse weak = api.post("/api/v1/me/password", "{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"curta\"}");
        assertThat(weak.getStatus()).isEqualTo(422);
        MockHttpServletResponse changed = api.post("/api/v1/me/password",
                "{\"currentPassword\":\"" + PASSWORD + "\",\"newPassword\":\"outra frase bem comprida 2027\"}");
        assertThat(changed.getStatus()).isEqualTo(200);
        assertThat(changed.getContentAsString()).contains("\"otherSessionsEnded\":1");
        assertThat(phone.get("/api/v1/me").getStatus()).isEqualTo(401);
        assertThat(api.get("/api/v1/me").getStatus()).isEqualTo(200);

        api.post("/api/v1/auth/logout", null);
        assertThat(api.login(email, PASSWORD).getStatus()).isEqualTo(401);
        assertThat(api.login(email, "outra frase bem comprida 2027").getStatus()).isEqualTo(200);
    }

    private static String now(byte[] secret) {
        return Totp.codeAt(secret, Instant.now().getEpochSecond());
    }

    private static String group(Pattern p, String body) {
        Matcher m = p.matcher(body);
        assertThat(m.find()).as("padrão %s em %s", p, body).isTrue();
        return m.group(1);
    }

    private static byte[] base32Decode(String s) {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int buffer = 0, bits = 0;
        for (char c : s.toCharArray()) {
            buffer = (buffer << 5) | alphabet.indexOf(c);
            bits += 5;
            if (bits >= 8) {
                out.write((buffer >> (bits - 8)) & 0xFF);
                bits -= 8;
            }
        }
        return out.toByteArray();
    }
}
