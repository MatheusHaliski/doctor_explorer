package com.doctorexplorer.core.credentialing.domain;

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

/** RF12 · Papel global além de PROFESSIONAL (implícito). Exige 2FA ativo para usar as rotas protegidas. */
@Entity
@Table(name = "user_role")
@IdClass(UserRole.Key.class)
public class UserRole {

    public enum Role { MODERATOR, ADMIN, DPO, TECHNICAL_DIRECTOR }

    public static class Key implements Serializable {
        private UUID userId;
        private Role role;

        public Key() {}

        public Key(UUID userId, Role role) {
            this.userId = userId;
            this.role = role;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(userId, k.userId) && role == k.role;
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, role);
        }
    }

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(name = "role")
    private Role role;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt = Instant.now();

    @Column(name = "granted_by")
    private UUID grantedBy;

    protected UserRole() {}

    public UserRole(UUID userId, Role role, UUID grantedBy) {
        this.userId = userId;
        this.role = role;
        this.grantedBy = grantedBy;
    }

    public UUID getUserId() { return userId; }
    public Role getRole() { return role; }
}
