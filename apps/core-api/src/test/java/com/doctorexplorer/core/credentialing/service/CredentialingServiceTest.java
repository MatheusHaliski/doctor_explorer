package com.doctorexplorer.core.credentialing.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CredentialingServiceTest {

    @Test
    void namesMatchIgnoringTitlesAccentsAndCase() {
        assertThat(CredentialingService.sameName("Dra. Ana Ribeiro", "ANA RIBEIRO")).isTrue();
        assertThat(CredentialingService.sameName("Dr. José  Álvares", "jose alvares")).isTrue();
        assertThat(CredentialingService.sameName("Ana Ribeiro", "Ana Ribeiro Silva")).isFalse();
        assertThat(CredentialingService.sameName(null, "x")).isFalse();
    }
}
