package com.doctorexplorer.clinical.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.doctorexplorer.clinical.config.ClinicalProperties;
import com.doctorexplorer.clinical.support.TestTokens;
import com.doctorexplorer.clinical.support.TestTokens.Doctor;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class InternalTokenVerifierTest {

    private final InternalTokenVerifier verifier = new InternalTokenVerifier(new ClinicalProperties("x",
            new ClinicalProperties.Core("http://localhost:1", TestTokens.publicKeyBase64(), Duration.ofSeconds(1))));

    @Test
    void acceptsOnlySignedUnexpiredTokensForThisService() {
        Doctor d = Doctor.random("Dra. Ana Ribeiro");
        String ok = TestTokens.token(d, true, true, "VERIFIED");
        assertThat(verifier.verify(ok)).hasValueSatisfying(c -> {
            assertThat(c.userId()).isEqualTo(d.id());
            assertThat(c.write()).isTrue();
            assertThat(c.sudoActive()).isTrue();
            assertThat(c.verified()).isTrue();
        });
        assertThat(verifier.verify(TestTokens.token(d, true, true, "VERIFIED", "outro-servico", System.currentTimeMillis() + 60_000))).isEmpty();
        assertThat(verifier.verify(TestTokens.token(d, true, true, "VERIFIED", "clinical-api", System.currentTimeMillis() - 1))).as("vencido").isEmpty();
        String[] p = ok.split("\\.");
        String forged = p[0] + "." + p[1].substring(0, p[1].length() - 2) + "AA." + p[2];
        assertThat(verifier.verify(forged)).as("payload alterado").isEmpty();
        assertThat(verifier.verify("eyJhbGciOi.e30.x")).isEmpty();
        assertThat(verifier.verify(null)).isEmpty();
    }
}
