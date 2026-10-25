package com.doctorexplorer.clinical.documents;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Modelo de receita ou pedido do médico: itens e posologia prontos para reaproveitar, sem paciente. */
@Entity
@Table(name = "document_template")
public class DocumentTemplate {

    @Id
    private UUID id;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClinicalDocument.Kind kind;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String items;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected DocumentTemplate() {}

    public DocumentTemplate(UUID id, UUID authorId, ClinicalDocument.Kind kind, String name, String items) {
        this.id = id;
        this.authorId = authorId;
        this.kind = kind;
        this.name = name;
        this.items = items;
    }

    public UUID getId() { return id; }
    public UUID getAuthorId() { return authorId; }
    public ClinicalDocument.Kind getKind() { return kind; }
    public String getName() { return name; }
    public String getItems() { return items; }
    public Instant getCreatedAt() { return createdAt; }
}
