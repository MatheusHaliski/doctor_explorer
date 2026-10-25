package com.doctorexplorer.core.media;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MediaAssets extends JpaRepository<MediaAsset, UUID> {

    List<MediaAsset> findByPostIdAndState(UUID postId, MediaAsset.State state);

    long countByPostIdAndState(UUID postId, MediaAsset.State state);

    List<MediaAsset> findByStateAndUploadExpiresAtBefore(MediaAsset.State state, Instant now);

    List<MediaAsset> findByOriginalDeletedAtIsNullAndCreatedAtBefore(Instant limit);

    List<MediaAsset> findByIdInAndOwnerId(Collection<UUID> ids, UUID ownerId);
}
