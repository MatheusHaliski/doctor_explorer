package com.doctorexplorer.core.audit;

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

/** RF13 · pedido de exportação da auditoria (máquina de estados do doc). */
@Entity
@Table(name = "audit_export")
public class AuditExport {

    public enum State { SOLICITADA, GERANDO, PRONTA, BAIXADA, EXPIRADA, FALHOU }

    public static final Duration VALIDITY = Duration.ofHours(24);

    @Id
    private UUID id;

    @Column(name = "requested_by", nullable = false)
    private UUID requestedBy;

    @Column(nullable = false)
    private String scope;

    @Column(nullable = false)
    private String filters;

    @Column(name = "viewer_label")
    private String viewerLabel;

    @Column(nullable = false)
    private String justification;

    @Column(name = "reveal_patient", nullable = false)
    private boolean revealPatient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private State state = State.SOLICITADA;

    @Column(name = "object_key")
    private String objectKey;

    private String sha256;
    private String signature;

    @Column(name = "event_count")
    private Integer eventCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "ready_at")
    private Instant readyAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "downloaded_at")
    private Instant downloadedAt;

    protected AuditExport() {}

    public AuditExport(UUID requestedBy, String viewerLabel, String scope, String filters, String justification, boolean revealPatient) {
        this.id = UuidV7.generate();
        this.requestedBy = requestedBy;
        this.viewerLabel = viewerLabel;
        this.scope = scope;
        this.filters = filters;
        this.justification = justification;
        this.revealPatient = revealPatient;
    }

    public void generating() {
        require(State.SOLICITADA);
        state = State.GERANDO;
    }

    public void ready(String objectKey, String sha256, String signature, int count, Instant now) {
        require(State.GERANDO);
        this.objectKey = objectKey;
        this.sha256 = sha256;
        this.signature = signature;
        this.eventCount = count;
        this.readyAt = now;
        this.expiresAt = now.plus(VALIDITY);
        state = State.PRONTA;
    }

    public void failed() {
        state = State.FALHOU;
    }

    /** Emitir URL de download: só PRONTA/BAIXADA dentro das 24 h; depois disso vira EXPIRADA. */
    public boolean downloadable(Instant now) {
        if ((state == State.PRONTA || state == State.BAIXADA) && expiresAt != null && now.isAfter(expiresAt)) state = State.EXPIRADA;
        return state == State.PRONTA || state == State.BAIXADA;
    }

    public void downloaded(Instant now) {
        if (downloadedAt == null) downloadedAt = now;
        state = State.BAIXADA;
    }

    private void require(State s) {
        if (state != s) throw new IllegalStateException("exportação em " + state + ", esperado " + s);
    }

    public UUID getId() { return id; }
    public UUID getRequestedBy() { return requestedBy; }

    public String getViewerLabel() { return viewerLabel; }
    public String getScope() { return scope; }
    public String getFilters() { return filters; }
    public String getJustification() { return justification; }
    public boolean isRevealPatient() { return revealPatient; }
    public State getState() { return state; }
    public String getObjectKey() { return objectKey; }
    public String getSha256() { return sha256; }
    public String getSignature() { return signature; }
    public Integer getEventCount() { return eventCount; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
}
