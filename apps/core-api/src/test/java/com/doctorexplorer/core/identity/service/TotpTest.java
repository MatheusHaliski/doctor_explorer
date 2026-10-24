package com.doctorexplorer.core.identity.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

class TotpTest {

    /** Vetores do RFC 6238, Apêndice B (SHA1, segredo "12345678901234567890"), truncados a 6 dígitos. */
    static final byte[] RFC_SECRET = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);

    @ParameterizedTest
    @CsvSource({
        "59, 287082",
        "1111111109, 081804",
        "1111111111, 050471",
        "1234567890, 005924",
        "2000000000, 279037",
        "20000000000, 353130",
    })
    void matchesRfc6238Vectors(long epochSeconds, String expected) {
        assertThat(Totp.codeAt(RFC_SECRET, epochSeconds)).isEqualTo(expected);
    }

    @Test
    void acceptsNeighbourStepsOnly() {
        long t = 1_700_000_000L;
        String current = Totp.codeAt(RFC_SECRET, t);
        assertThat(Totp.verify(RFC_SECRET, current, t)).isTrue();
        assertThat(Totp.verify(RFC_SECRET, current, t + 30)).isTrue();
        assertThat(Totp.verify(RFC_SECRET, current, t - 30)).isTrue();
        assertThat(Totp.verify(RFC_SECRET, current, t + 90)).isFalse();
        assertThat(Totp.verify(RFC_SECRET, "12 34 56", t)).isFalse();
        assertThat(Totp.verify(RFC_SECRET, null, t)).isFalse();
    }

    @Test
    void base32AndOtpauthUri() {
        assertThat(Totp.base32("Hello!".getBytes(StandardCharsets.US_ASCII))).isEqualTo("JBSWY3DPEE");
        String uri = Totp.otpauthUri(RFC_SECRET, "Doctor Explorer", "ana@example.org");
        // segredo público do RFC 6238 ("12345678901234567890" em base32), não é credencial
        assertThat(uri).startsWith("otpauth://totp/Doctor%20Explorer:ana%40example.org?secret=GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ") // gitleaks:allow
                .contains("issuer=Doctor%20Explorer").contains("digits=6").contains("period=30");
    }
}
