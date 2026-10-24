package com.doctorexplorer.core.privacy.domain;

import com.doctorexplorer.core.common.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** Art. 18, V · cópia dos dados em JSON versionado, disponível por 24 h para o próprio titular. */
@Entity
@Table(name = "data_export_request")
public class DataExportRequest {

    public static final Duration AVAILABILITY = Duration.ofHours(24);

    public enum State { REQUESTED, GENERATING, READY, DOWNLOADED, EXPIRED, FAILED }

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private State state;

    @Column(name = "format_version", nullable = false)
    private String formatVersion = "1";

    @Column
    private String payload;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "ready_at")
    private Instant readyAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "downloaded_at")
    private Instant downloadedAt;

    protected DataExportRequest() {}

    public DataExportRequest(UUID userId, String payload) {
        this.id = UuidV7.generate();
        this.userId = userId;
        this.payload = payload;
        this.state = State.READY;
        this.requestedAt = Instant.now();
        this.readyAt = requestedAt;
        this.expiresAt = requestedAt.plus(AVAILABILITY);
    }

    public boolean isAvailable(Instant now) {
        return (state == State.READY || state == State.DOWNLOADED) && now.isBefore(expiresAt);
    }

    public void markDownloaded() {
        this.state = State.DOWNLOADED;
        this.downloadedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public State getState() { return state; }
    public String getFormatVersion() { return formatVersion; }
    public String getPayload() { return payload; }
    public Instant getRequestedAt() { return requestedAt; }
    public Instant getExpiresAt() { return expiresAt; }
}
