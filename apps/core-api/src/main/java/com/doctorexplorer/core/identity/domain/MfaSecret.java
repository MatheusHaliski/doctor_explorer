package com.doctorexplorer.core.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Segredo TOTP cifrado (AES-256-GCM, subchave "mfa"). Pendente até o usuário confirmar o primeiro código. */
@Entity
@Table(name = "mfa_secret")
public class MfaSecret {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "totp_secret_cipher", nullable = false)
    private byte[] totpSecretCipher;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected MfaSecret() {}

    public MfaSecret(UUID userId, byte[] totpSecretCipher) {
        this.userId = userId;
        this.totpSecretCipher = totpSecretCipher;
        this.enabled = false;
        this.createdAt = Instant.now();
    }

    public void confirm() {
        this.enabled = true;
        this.confirmedAt = Instant.now();
    }

    public UUID getUserId() { return userId; }
    public byte[] getTotpSecretCipher() { return totpSecretCipher; }
    public boolean isEnabled() { return enabled; }
    public Instant getConfirmedAt() { return confirmedAt; }
}
