package com.doctorexplorer.core.community.domain;

import com.doctorexplorer.core.common.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Comentário; {@code isOpinion} marca "parecer" (opinião técnica), que muda o estado do caso para "em discussão". */
@Entity
@Table(name = "post_comment")
public class PostComment {

    @Id
    private UUID id;

    @Column(name = "post_id", nullable = false)
    private UUID postId;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Column(nullable = false)
    private String body;

    @Column(name = "is_opinion", nullable = false)
    private boolean opinion;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "hidden_reason")
    private String hiddenReason;

    protected PostComment() {}

    public PostComment(UUID postId, UUID authorId, String body, boolean opinion) {
        this.id = UuidV7.generate();
        this.postId = postId;
        this.authorId = authorId;
        this.body = body.trim();
        this.opinion = opinion;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getPostId() { return postId; }
    public UUID getAuthorId() { return authorId; }
    public String getBody() { return body; }
    public boolean isOpinion() { return opinion; }
    public Instant getCreatedAt() { return createdAt; }
    public boolean isHidden() { return hiddenReason != null; }
}
