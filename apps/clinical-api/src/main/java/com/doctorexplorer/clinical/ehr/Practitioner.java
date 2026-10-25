package com.doctorexplorer.clinical.ehr;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Retrato do médico (id da conta na core-api, nome e CRM da última chamada; gravado por upsert) para autoria e FHIR. */
@Entity
@Table(name = "practitioner")
public class Practitioner {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(name = "crm_number")
    private String crmNumber;

    @Column(name = "crm_uf")
    private String crmUf;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Practitioner() {}

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getCrmNumber() { return crmNumber; }
    public String getCrmUf() { return crmUf; }
}
