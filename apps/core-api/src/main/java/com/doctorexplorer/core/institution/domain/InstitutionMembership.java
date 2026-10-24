package com.doctorexplorer.core.institution.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** RF11/RF12 · Vínculo médico–instituição com papel institucional. Aceito pelos dois lados. */
@Entity
@Table(name = "institution_membership")
@IdClass(InstitutionMembership.Key.class)
public class InstitutionMembership {

    /** Ordem = hierarquia: quem concede só concede papel igual ou abaixo do seu. */
    public enum Role {
        NETWORK_ADMIN(6), HOSPITAL_ADMIN(5), CLINICAL_DIRECTOR(4), UNIT_MANAGER(3), AUDITOR(2), STAFF_PHYSICIAN(1);

        public final int rank;

        Role(int rank) {
            this.rank = rank;
        }

        public boolean canManageMembers() {
            return rank >= UNIT_MANAGER.rank;
        }

        public boolean canPublishInstitutional() {
            return rank >= UNIT_MANAGER.rank && this != AUDITOR;
        }

        public boolean canViewAudit() {
            return this == AUDITOR || rank >= HOSPITAL_ADMIN.rank;
        }
    }

    public enum State { INVITED, REQUESTED, ACTIVE, ENDED }

    public static class Key implements Serializable {
        private UUID institutionId;
        private UUID userId;

        public Key() {}

        public Key(UUID institutionId, UUID userId) {
            this.institutionId = institutionId;
            this.userId = userId;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(institutionId, k.institutionId) && Objects.equals(userId, k.userId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(institutionId, userId);
        }
    }

    @Id
    @Column(name = "institution_id")
    private UUID institutionId;

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private State state;

    @Column(nullable = false)
    private Instant since = Instant.now();

    protected InstitutionMembership() {}

    public InstitutionMembership(UUID institutionId, UUID userId, Role role, State state) {
        this.institutionId = institutionId;
        this.userId = userId;
        this.role = role;
        this.state = state;
    }

    public void activate() {
        this.state = State.ACTIVE;
        this.since = Instant.now();
    }

    public void end() {
        this.state = State.ENDED;
    }

    public void setRole(Role role) {
        this.role = role;
    }

    public UUID getInstitutionId() { return institutionId; }
    public UUID getUserId() { return userId; }
    public Role getRole() { return role; }
    public State getState() { return state; }
    public Instant getSince() { return since; }
}
