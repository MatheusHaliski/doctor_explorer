package com.doctorexplorer.core.community.domain;

import com.doctorexplorer.core.common.UuidV7;
import com.doctorexplorer.core.identity.domain.InvalidTransitionException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * RF04 · Publicação anonimizada do feed. Eixos de classificação: {@link Kind} (tipo), especialidade (grupo),
 * {@link Audience} (origem) e estado derivado ("sem resposta" = publicada sem parecer).
 */
@Entity
@Table(name = "case_post")
public class CasePost {

    public enum Kind { CASE, OPINION_REQUEST, PROTOCOL, NOTICE, EDUCATION }

    public enum Audience { GROUP, NETWORK, INSTITUTION }

    public enum State { DRAFT, PUBLISHED, RESOLVED, HIDDEN, DELETED }

    /** Estado como o feed mostra: derivado de state + opinion_count. */
    public enum FeedState { SEM_RESPOSTA, EM_DISCUSSAO, RESOLVIDO }

    public record Sections(String complaint, String history, String exam, String workup, String question) {}

    @Id
    private UUID id;

    @Column(name = "group_id", nullable = false)
    private UUID groupId;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Kind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Audience audience;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private State state;

    @Column(nullable = false)
    private String title;

    @Column private String complaint;
    @Column private String history;
    @Column private String exam;
    @Column private String workup;
    @Column private String question;

    @Column(nullable = false)
    private String tags = "";

    @Column(name = "comment_count", nullable = false)
    private int commentCount;

    @Column(name = "opinion_count", nullable = false)
    private int opinionCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "hidden_reason")
    private String hiddenReason;

    @Column(name = "institution_id")
    private UUID institutionId;

    protected CasePost() {}

    public CasePost(UUID groupId, UUID authorId, Kind kind, Audience audience, String title, Sections s, List<String> tags) {
        this.id = UuidV7.generate();
        this.groupId = groupId;
        this.authorId = authorId;
        this.kind = kind;
        this.audience = audience;
        this.state = State.PUBLISHED;
        this.title = title.trim();
        this.complaint = s.complaint();
        this.history = s.history();
        this.exam = s.exam();
        this.workup = s.workup();
        this.question = s.question();
        this.tags = tags == null ? "" : String.join(",", tags.stream().map(t -> t.trim().toLowerCase().replace("#", "")).filter(t -> !t.isEmpty()).toList());
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public void registerComment(boolean opinion) {
        commentCount++;
        if (opinion) opinionCount++;
        updatedAt = Instant.now();
    }

    public void resolve() {
        if (state != State.PUBLISHED) throw new InvalidTransitionException(state, State.RESOLVED);
        state = State.RESOLVED;
        resolvedAt = Instant.now();
        updatedAt = resolvedAt;
    }

    public void hide(String reason) {
        if (state != State.PUBLISHED && state != State.RESOLVED) throw new InvalidTransitionException(state, State.HIDDEN);
        state = State.HIDDEN;
        hiddenReason = reason;
        updatedAt = Instant.now();
    }

    public void delete() {
        if (state == State.DELETED) throw new InvalidTransitionException(state, State.DELETED);
        state = State.DELETED;
        updatedAt = Instant.now();
    }

    public void setInstitutionId(UUID institutionId) {
        this.institutionId = institutionId;
    }

    public UUID getInstitutionId() { return institutionId; }

    public FeedState feedState() {
        if (state == State.RESOLVED) return FeedState.RESOLVIDO;
        if (kind == Kind.PROTOCOL || kind == Kind.NOTICE) return FeedState.RESOLVIDO; // nascem resolvidos
        return opinionCount == 0 ? FeedState.SEM_RESPOSTA : FeedState.EM_DISCUSSAO;
    }

    public List<String> tagList() {
        return tags.isEmpty() ? List.of() : Arrays.asList(tags.split(","));
    }

    public boolean isVisible() {
        return state == State.PUBLISHED || state == State.RESOLVED;
    }

    public UUID getId() { return id; }
    public UUID getGroupId() { return groupId; }
    public UUID getAuthorId() { return authorId; }
    public Kind getKind() { return kind; }
    public Audience getAudience() { return audience; }
    public State getState() { return state; }
    public String getTitle() { return title; }
    public Sections sections() { return new Sections(complaint, history, exam, workup, question); }
    public int getCommentCount() { return commentCount; }
    public int getOpinionCount() { return opinionCount; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getResolvedAt() { return resolvedAt; }
}
