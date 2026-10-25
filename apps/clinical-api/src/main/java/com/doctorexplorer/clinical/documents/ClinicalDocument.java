package com.doctorexplorer.clinical.documents;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Receita ou pedido de exame (RF09). Conteúdo e PDF cifrados com a chave do cofre; assinado não muda nem se apaga. */
@Entity
@Table(name = "clinical_document")
public class ClinicalDocument {

    public enum Kind { RECEITA, EXAME }

    public enum State { RASCUNHO, ASSINANDO, FALHA_ASSINATURA, ASSINADA, ENVIADA, CANCELADA }

    @Id
    private UUID id;

    @Column(name = "vault_id", nullable = false)
    private UUID vaultId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "encounter_id", nullable = false)
    private UUID encounterId;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Kind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private State state = State.RASCUNHO;

    @Column(name = "content_enc")
    private byte[] contentEnc;

    @Column(name = "content_hash")
    private String contentHash;

    @Column(name = "validation_code_hash")
    private String validationCodeHash;

    @Column(name = "validation_code_enc")
    private byte[] validationCodeEnc;

    @Column(name = "pdf_enc")
    private byte[] pdfEnc;

    @Column(name = "pdf_sha256")
    private String pdfSha256;

    @Column(name = "signature_level")
    private String signatureLevel;

    @Column(name = "signature_provider")
    private String signatureProvider;

    @Column(name = "cert_subject")
    private String certSubject;

    @Column(name = "cert_serial")
    private String certSerial;

    @Column(name = "signed_at")
    private Instant signedAt;

    @Column(name = "valid_until")
    private LocalDate validUntil;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "cancel_reason")
    private String cancelReason;

    @Column(name = "canceled_at")
    private Instant canceledAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected ClinicalDocument() {}

    public ClinicalDocument(UUID id, UUID vaultId, UUID patientId, UUID encounterId, UUID authorId, Kind kind) {
        this.id = id;
        this.vaultId = vaultId;
        this.patientId = patientId;
        this.encounterId = encounterId;
        this.authorId = authorId;
        this.kind = kind;
    }

    public boolean editable() {
        return state == State.RASCUNHO || state == State.FALHA_ASSINATURA;
    }

    public void saveDraft(byte[] contentEnc, String contentHash) {
        if (!editable()) throw new IllegalStateException("documento fora do rascunho");
        this.contentEnc = contentEnc;
        this.contentHash = contentHash;
        this.state = State.RASCUNHO;
        this.failureReason = null;
        this.updatedAt = Instant.now();
    }

    public void signing() {
        this.state = State.ASSINANDO;
        this.updatedAt = Instant.now();
    }

    public void signed(String codeHash, byte[] codeEnc, byte[] pdfEnc, String pdfSha256, String level, String provider, String certSubject, String certSerial,
            Instant at, LocalDate validUntil) {
        if (state != State.ASSINANDO) throw new IllegalStateException("só um documento em assinatura vira assinado");
        this.validationCodeHash = codeHash;
        this.validationCodeEnc = codeEnc;
        this.pdfEnc = pdfEnc;
        this.pdfSha256 = pdfSha256;
        this.signatureLevel = level;
        this.signatureProvider = provider;
        this.certSubject = certSubject;
        this.certSerial = certSerial;
        this.signedAt = at;
        this.validUntil = validUntil;
        this.state = State.ASSINADA;
        this.updatedAt = at;
    }

    public void signatureFailed(String reason) {
        this.state = State.FALHA_ASSINATURA;
        this.failureReason = reason;
        this.updatedAt = Instant.now();
    }

    public void delivered() {
        if (state == State.ASSINADA) this.state = State.ENVIADA;
        this.updatedAt = Instant.now();
    }

    public void cancel(String reason) {
        this.state = State.CANCELADA;
        this.cancelReason = reason;
        this.canceledAt = Instant.now();
        this.updatedAt = canceledAt;
    }

    public boolean signedOrAfter() {
        return state == State.ASSINADA || state == State.ENVIADA || state == State.CANCELADA;
    }

    public UUID getId() { return id; }
    public UUID getVaultId() { return vaultId; }
    public UUID getPatientId() { return patientId; }
    public UUID getEncounterId() { return encounterId; }
    public UUID getAuthorId() { return authorId; }
    public Kind getKind() { return kind; }
    public State getState() { return state; }
    public byte[] getContentEnc() { return contentEnc; }
    public String getContentHash() { return contentHash; }
    public byte[] getValidationCodeEnc() { return validationCodeEnc; }
    public byte[] getPdfEnc() { return pdfEnc; }
    public String getPdfSha256() { return pdfSha256; }
    public String getSignatureLevel() { return signatureLevel; }
    public String getSignatureProvider() { return signatureProvider; }
    public String getCertSubject() { return certSubject; }
    public Instant getSignedAt() { return signedAt; }
    public LocalDate getValidUntil() { return validUntil; }
    public String getFailureReason() { return failureReason; }
    public String getCancelReason() { return cancelReason; }
    public Instant getCanceledAt() { return canceledAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
