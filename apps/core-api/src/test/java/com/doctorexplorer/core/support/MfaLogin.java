package com.doctorexplorer.core.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.doctorexplorer.core.access.AccessMatrixIT;
import com.doctorexplorer.core.identity.service.Totp;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Ativa o 2FA de uma conta já logada e entra de novo em duas etapas (papéis sensíveis exigem isso, RF12). */
public final class MfaLogin {

    private MfaLogin() {}

    /** Devolve o segredo TOTP para gerar códigos depois. */
    public static byte[] enableAndRelogin(ApiClient c, String email, String password) throws Exception {
        assertThat(c.post("/api/v1/me/sudo", "{\"password\":\"" + password + "\"}").getStatus()).isEqualTo(200);
        String setup = c.post("/api/v1/me/mfa/totp/setup", null).getContentAsString();
        Matcher m = Pattern.compile("\"secret\":\"([A-Z2-7]+)\"").matcher(setup);
        assertThat(m.find()).as(setup).isTrue();
        byte[] secret = AccessMatrixIT.base32Decode(m.group(1));
        assertThat(c.post("/api/v1/me/mfa/totp/confirm", "{\"code\":\"" + Totp.codeAt(secret, Instant.now().getEpochSecond()) + "\"}").getStatus()).isEqualTo(200);
        c.post("/api/v1/auth/logout", null);
        c.login(email, password);
        assertThat(c.post("/api/v1/auth/login/2fa", "{\"code\":\"" + Totp.codeAt(secret, Instant.now().getEpochSecond()) + "\"}").getStatus()).isEqualTo(200);
        return secret;
    }
}
