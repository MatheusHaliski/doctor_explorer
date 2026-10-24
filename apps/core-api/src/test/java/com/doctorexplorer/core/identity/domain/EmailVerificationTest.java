package com.doctorexplorer.core.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.doctorexplorer.core.common.UuidV7;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class EmailVerificationTest {

    @Test
    void acceptsTheRightCodeWithinValidity() {
        var issued = EmailVerification.issue(UuidV7.generate());
        assertThat(issued.code()).matches("\\d{6}");
        assertThat(issued.verification().verify(issued.code(), Instant.now())).isEqualTo(EmailVerification.Outcome.OK);
    }

    @Test
    void expiresAfterThirtyMinutes() {
        var issued = EmailVerification.issue(UuidV7.generate());
        Instant later = Instant.now().plus(Duration.ofMinutes(31));
        assertThat(issued.verification().verify(issued.code(), later)).isEqualTo(EmailVerification.Outcome.EXPIRED);
    }

    @Test
    void locksAfterFiveWrongAttempts() {
        var issued = EmailVerification.issue(UuidV7.generate());
        EmailVerification v = issued.verification();
        for (int i = 0; i < 4; i++) assertThat(v.verify("000000", Instant.now())).isEqualTo(EmailVerification.Outcome.WRONG_CODE);
        assertThat(v.verify("000000", Instant.now())).isEqualTo(EmailVerification.Outcome.TOO_MANY_ATTEMPTS);
        assertThat(v.verify(issued.code(), Instant.now())).isEqualTo(EmailVerification.Outcome.TOO_MANY_ATTEMPTS);
    }

    @Test
    void resendOnlyAfterOneMinute() {
        var v = EmailVerification.issue(UuidV7.generate()).verification();
        assertThat(v.canResend(Instant.now())).isFalse();
        assertThat(v.canResend(Instant.now().plusSeconds(61))).isTrue();
    }

    @Test
    void uuidV7IsVersion7AndTimeOrdered() throws InterruptedException {
        var a = UuidV7.generate();
        Thread.sleep(2);
        var b = UuidV7.generate();
        assertThat(a.version()).isEqualTo(7);
        assertThat(a.variant()).isEqualTo(2);
        assertThat(a.toString().compareTo(b.toString())).isLessThan(0);
    }
}
