package com.doctorexplorer.core.identity.domain;

import com.doctorexplorer.core.common.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "user_account")
public class UserAccount {

    public static final int DELETION_GRACE_DAYS = 30;

    @Id
    private UUID id;

    @Column(nullable = false)
    private String email;

    @Column(name = "email_normalized", nullable = false)
    private String emailNormalized;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "deletion_requested_at")
    private Instant deletionRequestedAt;

    @Column(name = "grace_ends_at")
    private Instant graceEndsAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected UserAccount() {}

    public static UserAccount register(String email, String displayName, LocalDate birthDate) {
        UserAccount a = new UserAccount();
        a.id = UuidV7.generate();
        a.email = email.trim();
        a.emailNormalized = normalizeEmail(email);
        a.displayName = displayName.trim();
        a.birthDate = birthDate;
        a.status = AccountStatus.PENDING_VERIFICATION;
        a.createdAt = Instant.now();
        a.updatedAt = a.createdAt;
        return a;
    }

    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public void activate() {
        status = status.transitionTo(AccountStatus.ACTIVE);
        activatedAt = Instant.now();
        touch();
    }

    public void suspend() {
        status = status.transitionTo(AccountStatus.SUSPENDED);
        touch();
    }

    public void reinstate() {
        status = status.transitionTo(AccountStatus.ACTIVE);
        touch();
    }

    public void requestDeletion(Instant now) {
        status = status.transitionTo(AccountStatus.PENDING_DELETION);
        deletionRequestedAt = now;
        graceEndsAt = now.plus(DELETION_GRACE_DAYS, ChronoUnit.DAYS);
        touch();
    }

    public void reactivate() {
        status = status.transitionTo(AccountStatus.ACTIVE);
        deletionRequestedAt = null;
        graceEndsAt = null;
        touch();
    }

    /** Eliminação (saga RF10): troca identificadores por valores anônimos; a linha fica como lápide. */
    public void anonymize(Instant now) {
        status = status.transitionTo(AccountStatus.ANONYMOUS_LGPD_DELETED);
        email = "deleted+" + id + "@anonymous.invalid";
        emailNormalized = email;
        displayName = "Conta excluída";
        birthDate = LocalDate.of(1900, 1, 1);
        deletedAt = now;
        touch();
    }

    private void touch() {
        updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getEmail() { return email; }
    public String getEmailNormalized() { return emailNormalized; }
    public String getDisplayName() { return displayName; }
    public LocalDate getBirthDate() { return birthDate; }
    public AccountStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getActivatedAt() { return activatedAt; }
    public Instant getGraceEndsAt() { return graceEndsAt; }
}
