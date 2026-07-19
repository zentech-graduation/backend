package com.app.modules.story.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.app.modules.media.entity.MediaAsset;

/**
 * Module-local read-only repository over the media module's {@link MediaAsset} entity.
 *
 * <p>Mirrors the {@code PostMediaAssetRepository} precedent: cross-module data is read through a
 * repository owned by this module instead of injecting another module's repository bean. Used to
 * validate media ownership at story creation and to hydrate media URLs in responses.
 */
@Repository
public interface StoryMediaAssetRepository extends JpaRepository<MediaAsset, UUID> {}
