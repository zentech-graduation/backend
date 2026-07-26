package com.app.modules.message.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.app.modules.media.entity.MediaAsset;

/**
 * Module-local read-only repository over the media module's {@link MediaAsset} entity.
 *
 * <p>Mirrors the {@code StoryMediaAssetRepository} precedent: cross-module data is read through a
 * repository owned by this module instead of injecting another module's repository bean. Used to
 * validate that an image/video message's referenced asset exists.
 */
@Repository
public interface MessageMediaAssetRepository extends JpaRepository<MediaAsset, UUID> {

    /** True only for an asset that exists and belongs to the given owner. */
    boolean existsByIdAndUserId(UUID id, UUID userId);
}
