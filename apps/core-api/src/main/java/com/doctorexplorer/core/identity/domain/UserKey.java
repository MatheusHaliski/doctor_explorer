package com.doctorexplorer.core.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * DEK social do usuário, envelopada pela KEK (subchave "user-kek" em dev; KMS em produção, ADR-011).
 * A eliminação destrói a DEK (crypto-shredding) e backups ficam ilegíveis.
 */
@Entity
@Table(name = "user_key")
public class UserKey {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "wrapped_dek", nullable = false)
    private byte[] wrappedDek;

    @Column(name = "kek_version", nullable = false)
    private int kekVersion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "destroyed_at")
    private Instant destroyedAt;

    protected UserKey() {}

    public UserKey(UUID userId, byte[] wrappedDek, int kekVersion) {
        this.userId = userId;
        this.wrappedDek = wrappedDek;
        this.kekVersion = kekVersion;
        this.createdAt = Instant.now();
    }

    public void destroy(Instant now) {
        this.wrappedDek = new byte[0];
        this.destroyedAt = now;
    }

    public UUID getUserId() { return userId; }
    public byte[] getWrappedDek() { return wrappedDek; }
    public int getKekVersion() { return kekVersion; }
    public boolean isDestroyed() { return destroyedAt != null; }
}
