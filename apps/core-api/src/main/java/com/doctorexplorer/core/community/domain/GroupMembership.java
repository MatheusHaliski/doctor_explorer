package com.doctorexplorer.core.community.domain;

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

@Entity
@Table(name = "group_membership")
@IdClass(GroupMembership.Key.class)
public class GroupMembership {

    public enum Role { MEMBER, MODERATOR }

    public enum State { REQUESTED, ACTIVE, REMOVED }

    public static class Key implements Serializable {
        private UUID groupId;
        private UUID userId;

        public Key() {}

        public Key(UUID groupId, UUID userId) {
            this.groupId = groupId;
            this.userId = userId;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(groupId, k.groupId) && Objects.equals(userId, k.userId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(groupId, userId);
        }
    }

    @Id
    @Column(name = "group_id")
    private UUID groupId;

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.MEMBER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private State state;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt = Instant.now();

    protected GroupMembership() {}

    public GroupMembership(UUID groupId, UUID userId, State state) {
        this.groupId = groupId;
        this.userId = userId;
        this.state = state;
    }

    public void approve() {
        this.state = State.ACTIVE;
        this.joinedAt = Instant.now();
    }

    public void remove() {
        this.state = State.REMOVED;
    }

    public UUID getGroupId() { return groupId; }
    public UUID getUserId() { return userId; }
    public Role getRole() { return role; }
    public State getState() { return state; }
    public Instant getJoinedAt() { return joinedAt; }
}
