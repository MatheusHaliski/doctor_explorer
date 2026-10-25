package com.doctorexplorer.core.media;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface MediaAssets extends JpaRepository<MediaAsset, UUID> {

    List<MediaAsset> findByPostIdAndState(UUID postId, MediaAsset.State state);

    long countByPostIdAndState(UUID postId, MediaAsset.State state);

    /** Trava a linha até o fim da transação: prévia, aprovação e descarte da mesma mídia ficam em fila. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MediaAsset m where m.id = :id")
    Optional<MediaAsset> lockById(UUID id);

    List<MediaAsset> findByStateAndStateChangedAtBefore(MediaAsset.State state, Instant limit);

    List<MediaAsset> findByStateAndUploadExpiresAtBefore(MediaAsset.State state, Instant now);

    List<MediaAsset> findByOriginalDeletedAtIsNullAndCreatedAtBefore(Instant limit);

    List<MediaAsset> findByIdInAndOwnerId(Collection<UUID> ids, UUID ownerId);
}
