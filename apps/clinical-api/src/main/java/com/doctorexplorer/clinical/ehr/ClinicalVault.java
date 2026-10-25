package com.doctorexplorer.clinical.ehr;

import com.doctorexplorer.clinical.common.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Cofre clínico: do médico (MVP) ou da instituição. Cada cofre tem a própria chave de dados embrulhada. */
@Entity
@Table(name = "clinical_vault")
public class ClinicalVault {

    public enum OwnerType { PRACTITIONER, INSTITUTION }

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false)
    private OwnerType ownerType;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "wrapped_dek", nullable = false)
    private byte[] wrappedDek;

    @Column(name = "key_id", nullable = false)
    private String keyId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected ClinicalVault() {}

    public static UUID newId() {
        return UuidV7.generate();
    }

    public UUID getId() { return id; }
    public OwnerType getOwnerType() { return ownerType; }
    public UUID getOwnerId() { return ownerId; }
    public byte[] getWrappedDek() { return wrappedDek; }
}
