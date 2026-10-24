package com.doctorexplorer.core.config;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Segredo do app ({@code DE_APP_SECRET}, Base64 de 32+ bytes) e derivação de subchaves por finalidade
 * (HKDF-SHA256, RFC 5869). Nada de segredo em .yml; fora de dev/it o {@link AppSecretValidator} recusa
 * subir sem ele.
 */
@ConfigurationProperties("doctor-explorer")
public record AppSecretProperties(
        String appSecret,
        @DefaultValue Session session,
        @DefaultValue Terms terms,
        @DefaultValue Jobs jobs) {

    public record Session(
            @DefaultValue("24h") Duration absoluteTimeout,
            @DefaultValue("__Host-de_session") String cookieName,
            @DefaultValue("true") boolean cookieSecure) {}

    public record Terms(@DefaultValue("2026-10-24") String version, @DefaultValue("2026-10-24") String privacyVersion) {}

    public record Jobs(@DefaultValue("0 15 3 * * *") String erasureCron) {}

    public boolean hasSecret() {
        return appSecret != null && !appSecret.isBlank();
    }

    public byte[] secretBytes() {
        byte[] bytes = Base64.getDecoder().decode(appSecret.trim());
        if (bytes.length < 32) {
            throw new IllegalStateException("DE_APP_SECRET precisa ter 32 bytes ou mais depois de decodificar o Base64.");
        }
        return bytes;
    }

    /** Subchave de 32 bytes para a finalidade informada ("mfa", "user-kek", "ip-hash"...). */
    public byte[] subkey(String purpose) {
        return hkdf(secretBytes(), ("doctor-explorer/" + purpose).getBytes(StandardCharsets.UTF_8), 32);
    }

    static byte[] hkdf(byte[] ikm, byte[] info, int length) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(new byte[32], "HmacSHA256")); // salt vazio = zeros (RFC 5869 §2.2)
            byte[] prk = mac.doFinal(ikm);
            mac.init(new SecretKeySpec(prk, "HmacSHA256"));
            byte[] out = new byte[length];
            byte[] t = new byte[0];
            int copied = 0;
            for (int i = 1; copied < length; i++) {
                mac.update(t);
                mac.update(info);
                mac.update((byte) i);
                t = mac.doFinal();
                int n = Math.min(t.length, length - copied);
                System.arraycopy(t, 0, out, copied, n);
                copied += n;
            }
            return out;
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
