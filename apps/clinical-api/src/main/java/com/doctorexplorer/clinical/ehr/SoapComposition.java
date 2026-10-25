package com.doctorexplorer.clinical.ehr;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Nota SOAP versionada (Composition do openEHR, ADR-003). Uma "linhagem" junta as versões da mesma nota: a versão
 * 1 é a original, as seguintes são retificações com motivo. Rascunho é editável; assinada é imutável (também no
 * banco, por gatilho); ao assinar uma retificação, a anterior passa a RETIFICADA e aponta para a nova.
 */
@Entity
@Table(name = "soap_composition")
public class SoapComposition {

    public enum State { RASCUNHO, ASSINADA, RETIFICADA, DESCARTADA }

    @Id
    private UUID id;

    @Column(name = "vault_id", nullable = false)
    private UUID vaultId;

    @Column(name = "encounter_id", nullable = false)
    private UUID encounterId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Column(name = "lineage_id", nullable = false)
    private UUID lineageId;

    @Column(nullable = false)
    private int version;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private State state = State.RASCUNHO;

    @Column(name = "subjective_enc")
    private byte[] subjectiveEnc;

    @Column(name = "objective_enc")
    private byte[] objectiveEnc;

    @Column(name = "assessment_enc")
    private byte[] assessmentEnc;

    @Column(name = "plan_enc")
    private byte[] planEnc;

    @Column(nullable = false)
    private String icd10 = "";

    @Column(nullable = false)
    private String snomed = "";

    @Column(nullable = false)
    private String links = "[]";

    @Column(name = "amend_reason")
    private String amendReason;

    @Column(name = "previous_id")
    private UUID previousId;

    @Column(name = "superseded_by")
    private UUID supersededBy;

    @Column(name = "content_hash")
    private String contentHash;

    private String signature;

    @Column(name = "signing_key_id")
    private String signingKeyId;

    @Column(name = "signed_at")
    private Instant signedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected SoapComposition() {}

    public SoapComposition(UUID id, UUID vaultId, UUID encounterId, UUID patientId, UUID authorId, UUID lineageId, int version, UUID previousId,
            String amendReason) {
        this.id = id;
        this.vaultId = vaultId;
        this.encounterId = encounterId;
        this.patientId = patientId;
        this.authorId = authorId;
        this.lineageId = lineageId;
        this.version = version;
        this.previousId = previousId;
        this.amendReason = amendReason;
    }

    public void saveDraft(byte[] s, byte[] o, byte[] a, byte[] p, List<String> icd10, List<String> snomed, String linksJson) {
        requireDraft();
        this.subjectiveEnc = s;
        this.objectiveEnc = o;
        this.assessmentEnc = a;
        this.planEnc = p;
        this.icd10 = String.join(",", icd10);
        this.snomed = String.join(",", snomed);
        this.links = linksJson;
        this.updatedAt = Instant.now();
    }

    public void sign(String contentHash, String signature, String keyId, Instant at) {
        requireDraft();
        this.state = State.ASSINADA;
        this.contentHash = contentHash;
        this.signature = signature;
        this.signingKeyId = keyId;
        this.signedAt = at;
        this.updatedAt = at;
    }

    public void supersededBy(UUID next) {
        if (state != State.ASSINADA) throw new IllegalStateException("só nota assinada é retificada");
        this.state = State.RETIFICADA;
        this.supersededBy = next;
        this.updatedAt = Instant.now();
    }

    public void discard() {
        requireDraft();
        this.state = State.DESCARTADA;
        // minimização: rascunho descartado não guarda conteúdo
        this.subjectiveEnc = null;
        this.objectiveEnc = null;
        this.assessmentEnc = null;
        this.planEnc = null;
        this.updatedAt = Instant.now();
    }

    private void requireDraft() {
        if (state != State.RASCUNHO) throw new IllegalStateException("nota fora do rascunho");
    }

    public List<String> icd10List() {
        return icd10.isBlank() ? List.of() : Arrays.asList(icd10.split(","));
    }

    public List<String> snomedList() {
        return snomed.isBlank() ? List.of() : Arrays.asList(snomed.split(","));
    }

    public UUID getId() { return id; }
    public UUID getVaultId() { return vaultId; }
    public UUID getEncounterId() { return encounterId; }
    public UUID getPatientId() { return patientId; }
    public UUID getAuthorId() { return authorId; }
    public UUID getLineageId() { return lineageId; }
    public int getVersion() { return version; }
    public State getState() { return state; }
    public byte[] getSubjectiveEnc() { return subjectiveEnc; }
    public byte[] getObjectiveEnc() { return objectiveEnc; }
    public byte[] getAssessmentEnc() { return assessmentEnc; }
    public byte[] getPlanEnc() { return planEnc; }
    public String getLinks() { return links; }
    public String getAmendReason() { return amendReason; }
    public UUID getPreviousId() { return previousId; }
    public UUID getSupersededBy() { return supersededBy; }
    public String getContentHash() { return contentHash; }
    public String getSignature() { return signature; }
    public String getSigningKeyId() { return signingKeyId; }
    public Instant getSignedAt() { return signedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
