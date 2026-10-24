package com.doctorexplorer.core.identity.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PasswordPolicyTest {

    PasswordPolicy policy = new PasswordPolicy();

    @Test
    void acceptsALongPassphrase() {
        assertThat(policy.validate("cavalo bateria grampo correto", "ana@example.org", "Ana Ribeiro")).isEmpty();
    }

    @Test
    void reportsEveryProblemAtOnce() {
        assertThat(policy.validate("ana1234", "ana1234@example.org", "Ana"))
                .hasSize(2)
                .anyMatch(m -> m.contains("pelo menos 12"))
                .anyMatch(m -> m.contains("conter seu e-mail"));
    }

    @Test
    void rejectsCommonPasswordsIgnoringCaseAndAccents() {
        assertThat(policy.validate("PasswordPassword", null, null)).anyMatch(m -> m.contains("muito comum"));
        assertThat(policy.validate("Administrádor", null, null)).anyMatch(m -> m.contains("muito comum"));
    }

    @Test
    void rejectsPasswordContainingDisplayName() {
        assertThat(policy.validate("Ana Ribeiro é intensivista", "x@y.org", "Ana Ribeiro")).anyMatch(m -> m.contains("conter seu nome"));
    }

    @Test
    void rejectsTooLong() {
        assertThat(policy.validate("a".repeat(129), null, null)).anyMatch(m -> m.contains("no máximo 128"));
    }
}
