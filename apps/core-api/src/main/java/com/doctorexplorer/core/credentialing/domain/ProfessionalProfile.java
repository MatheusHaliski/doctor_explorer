package com.doctorexplorer.core.credentialing.domain;

import com.doctorexplorer.core.identity.domain.SealStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/** RF02/RF03 · Perfil profissional com CRM/UF e selo. Mudar CRM volta o selo para PENDING. */
@Entity
@Table(name = "professional_profile")
public class ProfessionalProfile {

    public enum Visibility { VERIFIED_NETWORK, MY_INSTITUTION, PUBLIC_MINIMAL }

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "crm_number")
    private String crmNumber;

    @Column(name = "crm_uf")
    private String crmUf;

    @Enumerated(EnumType.STRING)
    @Column(name = "seal_status", nullable = false)
    private SealStatus seal = SealStatus.NONE;

    @Column(name = "seal_changed_at", nullable = false)
    private Instant sealChangedAt = Instant.now();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Visibility visibility = Visibility.VERIFIED_NETWORK;

    @Column
    private String bio;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected ProfessionalProfile() {}

    public ProfessionalProfile(UUID userId) {
        this.userId = userId;
    }

    /** Envia (ou reenvia) CRM/UF para verificação. De VERIFIED, qualquer mudança de CRM volta a PENDING. */
    public void submitCrm(String crmNumber, String crmUf) {
        this.crmNumber = crmNumber.replaceAll("\\D", "");
        this.crmUf = crmUf.trim().toUpperCase(Locale.ROOT);
        moveSeal(SealStatus.PENDING);
    }

    public void markVerified() { moveSeal(SealStatus.VERIFIED); }
    public void markRejected() { moveSeal(SealStatus.REJECTED); }
    public void markManualReview() { moveSeal(SealStatus.MANUAL_REVIEW); }
    public void markRevoked() { moveSeal(SealStatus.REVOKED); }

    public void edit(String bio, Visibility visibility) {
        this.bio = bio;
        this.visibility = visibility;
        this.updatedAt = Instant.now();
    }

    /** Nome, CRM ou especialidade de conta verificada mudou: o selo volta para análise. */
    public void onIdentityChanged() {
        if (seal == SealStatus.VERIFIED) moveSeal(SealStatus.PENDING);
    }

    private void moveSeal(SealStatus target) {
        this.seal = this.seal.transitionTo(target);
        this.sealChangedAt = Instant.now();
        this.updatedAt = sealChangedAt;
    }

    public UUID getUserId() { return userId; }
    public String getCrmNumber() { return crmNumber; }
    public String getCrmUf() { return crmUf; }
    public SealStatus getSeal() { return seal; }
    public Instant getSealChangedAt() { return sealChangedAt; }
    public Visibility getVisibility() { return visibility; }
    public String getBio() { return bio; }
}
