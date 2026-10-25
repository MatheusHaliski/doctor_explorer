package com.doctorexplorer.clinical.ehr;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Encontro (consulta, discussão de caso, teleconsulta ou interconsulta); cada um tem uma ou mais notas. */
@Entity
@Table(name = "encounter")
public class Encounter {

    public enum Context { CONSULTA, DISCUSSAO_DE_CASO, TELECONSULTA, INTERCONSULTA }

    @Id
    private UUID id;

    @Column(name = "vault_id", nullable = false)
    private UUID vaultId;

    @Column(name = "patient_id", nullable = false)
    private UUID patientId;

    @Column(name = "practitioner_id", nullable = false)
    private UUID practitionerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Context context;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Encounter() {}

    public Encounter(UUID id, UUID vaultId, UUID patientId, UUID practitionerId, Context context, Instant occurredAt) {
        this.id = id;
        this.vaultId = vaultId;
        this.patientId = patientId;
        this.practitionerId = practitionerId;
        this.context = context;
        this.occurredAt = occurredAt;
    }

    public UUID getId() { return id; }
    public UUID getVaultId() { return vaultId; }
    public UUID getPatientId() { return patientId; }
    public UUID getPractitionerId() { return practitionerId; }
    public Context getContext() { return context; }
    public Instant getOccurredAt() { return occurredAt; }
}
