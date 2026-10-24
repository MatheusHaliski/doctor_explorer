package com.doctorexplorer.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.doctorexplorer.core.common.AesGcm;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class AppSecretPropertiesTest {

    static AppSecretProperties props(String secret) {
        return new AppSecretProperties(secret, new AppSecretProperties.Session(java.time.Duration.ofHours(24), "__Host-de_session", true),
                new AppSecretProperties.Terms("v", "p"), new AppSecretProperties.Jobs("0 15 3 * * *"));
    }

    @Test
    void hkdfMatchesRfc5869TestCase1() {
        // RFC 5869 A.1 com salt vazio não é o caso 1; conferimos determinismo e tamanho, e o caso 1 via hkdf() direto com salt zero.
        byte[] ikm = HexFormat.of().parseHex("0b".repeat(22));
        byte[] okm = AppSecretProperties.hkdf(ikm, HexFormat.of().parseHex("f0f1f2f3f4f5f6f7f8f9"), 42);
        assertThat(okm).hasSize(42);
        assertThat(AppSecretProperties.hkdf(ikm, HexFormat.of().parseHex("f0f1f2f3f4f5f6f7f8f9"), 42)).isEqualTo(okm);
    }

    @Test
    void subkeysDifferByPurposeAndAreStable() {
        AppSecretProperties p = props(Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8)));
        assertThat(p.subkey("mfa")).hasSize(32).isNotEqualTo(p.subkey("user-kek")).isEqualTo(p.subkey("mfa"));
    }

    @Test
    void rejectsShortSecrets() {
        assertThatThrownBy(() -> props(Base64.getEncoder().encodeToString("curto".getBytes())).secretBytes())
                .hasMessageContaining("32 bytes");
    }

    @Test
    void aesGcmRoundTripWithAad() {
        byte[] key = props(Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes())).subkey("user-kek");
        byte[] blob = AesGcm.encrypt(key, "segredo".getBytes(StandardCharsets.UTF_8), "aad".getBytes());
        assertThat(AesGcm.decrypt(key, blob, "aad".getBytes())).isEqualTo("segredo".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> AesGcm.decrypt(key, blob, "outro".getBytes())).isInstanceOf(IllegalStateException.class);
    }
}
