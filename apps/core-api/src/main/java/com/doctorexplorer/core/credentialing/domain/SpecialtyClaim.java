package com.doctorexplorer.core.credentialing.domain;

import com.doctorexplorer.core.common.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "specialty_claim")
public class SpecialtyClaim {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "specialty_code", nullable = false)
    private String specialtyCode;

    @Column(nullable = false)
    private String rqe;

    @Column(nullable = false)
    private boolean confirmed;

    protected SpecialtyClaim() {}

    public SpecialtyClaim(UUID userId, String specialtyCode, String rqe) {
        this.id = UuidV7.generate();
        this.userId = userId;
        this.specialtyCode = specialtyCode.trim().toUpperCase();
        this.rqe = rqe.replaceAll("\\D", "");
    }

    public void setConfirmed(boolean confirmed) { this.confirmed = confirmed; }

    public UUID getId() { return id; }
    public UUID getUserId() { return userId; }
    public String getSpecialtyCode() { return specialtyCode; }
    public String getRqe() { return rqe; }
    public boolean isConfirmed() { return confirmed; }
}
