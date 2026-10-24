package com.doctorexplorer.core.credentialing.domain;

import com.doctorexplorer.core.common.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Evidência de uma consulta à fonte do CFM: quando, onde, hash da resposta, resultado e motivo em pt-BR. */
@Entity
@Table(name = "credential_check")
public class CredentialCheck {

    public enum Outcome { VERIFIED, REJECTED, UNAVAILABLE, NOT_FOUND }

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private String source;

    @Column(name = "checked_at", nullable = false)
    private Instant checkedAt = Instant.now();

    @Column(name = "response_hash")
    private String responseHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Outcome outcome;

    @Column(name = "reason_pt_br")
    private String reasonPtBr;

    protected CredentialCheck() {}

    public CredentialCheck(UUID userId, String source, String responseHash, Outcome outcome, String reasonPtBr) {
        this.id = UuidV7.generate();
        this.userId = userId;
        this.source = source;
        this.responseHash = responseHash;
        this.outcome = outcome;
        this.reasonPtBr = reasonPtBr;
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getSource() { return source; }
    public Instant getCheckedAt() { return checkedAt; }
    public Outcome getOutcome() { return outcome; }
    public String getReasonPtBr() { return reasonPtBr; }
}
