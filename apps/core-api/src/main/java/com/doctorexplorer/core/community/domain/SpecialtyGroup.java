package com.doctorexplorer.core.community.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** RF04 · Grupo fechado por especialidade. Entra automaticamente quem tem o RQE confirmado; os demais pedem. */
@Entity
@Table(name = "specialty_group")
public class SpecialtyGroup {

    public enum JoinPolicy { RQE_OR_APPROVAL, APPROVAL_ONLY }

    @Id
    private UUID id;

    @Column(name = "specialty_code", nullable = false, unique = true)
    private String specialtyCode;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "join_policy", nullable = false)
    private JoinPolicy joinPolicy = JoinPolicy.RQE_OR_APPROVAL;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected SpecialtyGroup() {}

    public UUID getId() { return id; }
    public String getSpecialtyCode() { return specialtyCode; }
    public String getName() { return name; }
    public JoinPolicy getJoinPolicy() { return joinPolicy; }
}
