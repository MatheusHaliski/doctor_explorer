package com.doctorexplorer.core.privacy.domain;

import com.doctorexplorer.core.common.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Histórico de consentimento: o estado atual de cada finalidade é o evento mais recente. Nunca se apaga uma linha. */
@Entity
@Table(name = "consent_event")
public class ConsentEvent {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ConsentPurpose purpose;

    @Column(nullable = false)
    private boolean granted;

    @Column(name = "text_version", nullable = false)
    private String textVersion;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "ip_hash", nullable = false)
    private String ipHash;

    protected ConsentEvent() {}

    public ConsentEvent(UUID userId, ConsentPurpose purpose, boolean granted, String textVersion, String ipHash) {
        this.id = UuidV7.generate();
        this.userId = userId;
        this.purpose = purpose;
        this.granted = granted;
        this.textVersion = textVersion;
        this.occurredAt = Instant.now();
        this.ipHash = ipHash;
    }

    public UUID getUserId() { return userId; }
    public ConsentPurpose getPurpose() { return purpose; }
    public boolean isGranted() { return granted; }
    public String getTextVersion() { return textVersion; }
    public Instant getOccurredAt() { return occurredAt; }
}
