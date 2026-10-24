package com.doctorexplorer.core.identity.domain;

import com.doctorexplorer.core.common.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "login_audit")
public class LoginAudit {

    public enum Outcome { SUCCESS, BAD_CREDENTIALS, MFA_REQUIRED, MFA_FAILED, LOCKED, LOGOUT }

    @Id
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Outcome outcome;

    @Column(name = "ip_hash", nullable = false)
    private String ipHash;

    @Column(name = "user_agent")
    private String userAgent;

    protected LoginAudit() {}

    public LoginAudit(UUID userId, Outcome outcome, String ipHash, String userAgent) {
        this.id = UuidV7.generate();
        this.userId = userId;
        this.occurredAt = Instant.now();
        this.outcome = outcome;
        this.ipHash = ipHash;
        this.userAgent = userAgent == null ? null : userAgent.substring(0, Math.min(userAgent.length(), 255));
    }

    public UUID getUserId() { return userId; }
    public Outcome getOutcome() { return outcome; }
    public Instant getOccurredAt() { return occurredAt; }
}
