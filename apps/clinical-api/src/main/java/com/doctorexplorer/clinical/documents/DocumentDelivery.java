package com.doctorexplorer.clinical.documents;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Link de uso único para o paciente baixar o PDF (7 dias). Só o hash do token fica no banco. */
@Entity
@Table(name = "document_delivery")
public class DocumentDelivery {

    @Id
    private UUID id;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(nullable = false)
    private String channel;

    @Column(name = "destination_masked")
    private String destinationMasked;

    @Column(name = "token_hash", nullable = false)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected DocumentDelivery() {}

    public DocumentDelivery(UUID id, UUID documentId, String channel, String destinationMasked, String tokenHash, Instant expiresAt) {
        this.id = id;
        this.documentId = documentId;
        this.channel = channel;
        this.destinationMasked = destinationMasked;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public boolean usable(Instant now) {
        return usedAt == null && now.isBefore(expiresAt);
    }

    public void used(Instant now) {
        this.usedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getDocumentId() { return documentId; }
    public String getChannel() { return channel; }
    public String getDestinationMasked() { return destinationMasked; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getUsedAt() { return usedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
