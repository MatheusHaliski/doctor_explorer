package com.doctorexplorer.clinical.ehr;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Paciente do cofre. Nome, nascimento e CPF ficam cifrados; o id é opaco e é o único que sai para a auditoria. */
@Entity
@Table(name = "patient")
public class Patient {

    @Id
    private UUID id;

    @Column(name = "vault_id", nullable = false)
    private UUID vaultId;

    @Column(name = "name_enc", nullable = false)
    private byte[] nameEnc;

    @Column(name = "birth_date_enc", nullable = false)
    private byte[] birthDateEnc;

    @Column(name = "cpf_enc")
    private byte[] cpfEnc;

    private String sex;

    @Column(name = "local_record_number")
    private String localRecordNumber;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "last_record_at", nullable = false)
    private Instant lastRecordAt = Instant.now();

    protected Patient() {}

    public Patient(UUID id, UUID vaultId, byte[] nameEnc, byte[] birthDateEnc, byte[] cpfEnc, String sex, String localRecordNumber, UUID createdBy) {
        this.id = id;
        this.vaultId = vaultId;
        this.nameEnc = nameEnc;
        this.birthDateEnc = birthDateEnc;
        this.cpfEnc = cpfEnc;
        this.sex = sex;
        this.localRecordNumber = localRecordNumber;
        this.createdBy = createdBy;
    }

    /** O prazo de guarda (20 anos) conta do último registro. */
    public void touched() {
        this.lastRecordAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getVaultId() { return vaultId; }
    public byte[] getNameEnc() { return nameEnc; }
    public byte[] getBirthDateEnc() { return birthDateEnc; }
    public byte[] getCpfEnc() { return cpfEnc; }
    public String getSex() { return sex; }
    public String getLocalRecordNumber() { return localRecordNumber; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastRecordAt() { return lastRecordAt; }
}
