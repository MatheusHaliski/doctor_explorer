package com.doctorexplorer.core.identity.domain;

import com.doctorexplorer.core.common.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Código de recuperação de uso único; só o hash fica no banco. */
@Entity
@Table(name = "recovery_code")
public class RecoveryCode {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "code_hash", nullable = false)
    private String codeHash;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected RecoveryCode() {}

    public RecoveryCode(UUID userId, String codeHash) {
        this.id = UuidV7.generate();
        this.userId = userId;
        this.codeHash = codeHash;
        this.createdAt = Instant.now();
    }

    public boolean isUsed() {
        return usedAt != null;
    }

    public void use(Instant now) {
        this.usedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getCodeHash() { return codeHash; }
}
