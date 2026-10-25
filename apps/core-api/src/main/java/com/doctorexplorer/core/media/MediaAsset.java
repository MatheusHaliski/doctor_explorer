package com.doctorexplorer.core.media;

import com.doctorexplorer.core.common.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** RF05 · uma mídia de caso e sua máquina de estados (docs/rf/RF05). */
@Entity
@Table(name = "media_asset")
public class MediaAsset {

    public enum Kind { IMAGE, DICOM }

    public enum State {
        UPLOAD_PENDENTE, QUARENTENA, PROCESSANDO, AGUARDANDO_REVISAO, APROVADO, DESCARTADO, EXPIRADO, REJEITADO, FALHOU;

        public boolean isFinal() {
            return this == APROVADO || this == DESCARTADO || this == EXPIRADO || this == REJEITADO || this == FALHOU;
        }
    }

    private static final Map<State, Set<State>> NEXT = Map.of(
            State.UPLOAD_PENDENTE, EnumSet.of(State.QUARENTENA, State.EXPIRADO, State.DESCARTADO),
            State.QUARENTENA, EnumSet.of(State.PROCESSANDO, State.REJEITADO, State.DESCARTADO),
            State.PROCESSANDO, EnumSet.of(State.AGUARDANDO_REVISAO, State.REJEITADO, State.FALHOU),
            State.AGUARDANDO_REVISAO, EnumSet.of(State.AGUARDANDO_REVISAO, State.APROVADO, State.DESCARTADO));

    public static final Duration UPLOAD_TTL = Duration.ofMinutes(15);

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Kind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private State state = State.UPLOAD_PENDENTE;

    @Column(name = "declared_type", nullable = false)
    private String declaredType;

    @Column(name = "declared_size", nullable = false)
    private long declaredSize;

    @Column(nullable = false)
    private boolean dermatology;

    @Column(name = "blur_faces")
    private Boolean blurFaces;

    @Column(name = "quarantine_key", nullable = false)
    private String quarantineKey;

    @Column(name = "preview_key")
    private String previewKey;

    @Column(name = "published_key")
    private String publishedKey;

    @Column(name = "output_type")
    private String outputType;

    private Integer width;
    private Integer height;

    @Column(name = "preview_version", nullable = false)
    private int previewVersion;

    private String report;
    private String reason;

    @Column(name = "post_id")
    private UUID postId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "upload_expires_at", nullable = false)
    private Instant uploadExpiresAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "original_deleted_at")
    private Instant originalDeletedAt;

    protected MediaAsset() {}

    public MediaAsset(UUID ownerId, Kind kind, String declaredType, long declaredSize, boolean dermatology, Boolean blurFaces) {
        this.id = UuidV7.generate();
        this.ownerId = ownerId;
        this.kind = kind;
        this.declaredType = declaredType;
        this.declaredSize = declaredSize;
        this.dermatology = dermatology;
        this.blurFaces = blurFaces;
        this.quarantineKey = "originals/" + id;
        this.uploadExpiresAt = createdAt.plus(UPLOAD_TTL);
    }

    private void move(State to) {
        if (!NEXT.getOrDefault(state, Set.of()).contains(to)) {
            throw new IllegalStateException("mídia em " + state + " não pode ir para " + to);
        }
        state = to;
    }

    public void received() { move(State.QUARENTENA); }

    public void processing() { move(State.PROCESSANDO); }

    public void readyForReview(String previewKey, String outputType, int width, int height, String reportJson, Instant now) {
        move(State.AGUARDANDO_REVISAO);
        this.previewKey = previewKey;
        this.outputType = outputType;
        this.width = width;
        this.height = height;
        this.report = reportJson;
        this.processedAt = now;
        this.previewVersion = 1;
    }

    public void redacted(String reportJson) {
        move(State.AGUARDANDO_REVISAO);
        this.report = reportJson;
        this.previewVersion++;
    }

    public void approved(String publishedKey, Instant now) {
        move(State.APROVADO);
        this.publishedKey = publishedKey;
        this.approvedAt = now;
    }

    public void rejected(String reason) {
        move(State.REJEITADO);
        this.reason = reason;
    }

    public void failed(String reason) {
        move(State.FALHOU);
        this.reason = reason;
    }

    public void discarded() { move(State.DESCARTADO); }

    public void expired() { move(State.EXPIRADO); }

    public void originalDeleted(Instant now) {
        if (originalDeletedAt == null) originalDeletedAt = now;
    }

    public void attachTo(UUID postId) {
        if (state != State.APROVADO) throw new IllegalStateException("só mídia aprovada entra em publicação");
        this.postId = postId;
    }

    /** Rosto é desfocado por padrão; em imagem dermatológica só se o autor escolher explicitamente (RF05, critério 4). */
    public boolean shouldBlurFaces() {
        // fora da dermatologia o desfoque é obrigatório: o autor não desliga a proteção de rosto
        return !dermatology || Boolean.TRUE.equals(blurFaces);
    }

    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public Kind getKind() { return kind; }
    public State getState() { return state; }
    public String getDeclaredType() { return declaredType; }
    public long getDeclaredSize() { return declaredSize; }
    public boolean isDermatology() { return dermatology; }
    public Boolean getBlurFaces() { return blurFaces; }
    public String getQuarantineKey() { return quarantineKey; }
    public String getPreviewKey() { return previewKey; }
    public String getPublishedKey() { return publishedKey; }
    public String getOutputType() { return outputType; }
    public Integer getWidth() { return width; }
    public Integer getHeight() { return height; }
    public int getPreviewVersion() { return previewVersion; }
    public String getReport() { return report; }
    public String getReason() { return reason; }
    public UUID getPostId() { return postId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUploadExpiresAt() { return uploadExpiresAt; }
    public Instant getOriginalDeletedAt() { return originalDeletedAt; }
}
