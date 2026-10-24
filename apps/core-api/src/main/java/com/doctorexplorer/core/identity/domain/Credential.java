package com.doctorexplorer.core.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "credential")
public class Credential {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;

    protected Credential() {}

    public Credential(UUID userId, String passwordHash) {
        this.userId = userId;
        this.passwordHash = passwordHash;
        this.changedAt = Instant.now();
    }

    public void change(String newHash) {
        this.passwordHash = newHash;
        this.changedAt = Instant.now();
    }

    public UUID getUserId() { return userId; }
    public String getPasswordHash() { return passwordHash; }
    public Instant getChangedAt() { return changedAt; }
}
