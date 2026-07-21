package com.app.modules.message.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.app.modules.story.entity.Story;

/**
 * Module-local read-only repository over the story module's {@link Story} entity.
 *
 * <p>Mirrors the {@code StoryMediaAssetRepository} precedent: cross-module data is read through a
 * repository owned by this module instead of injecting another module's repository bean. Used to
 * validate that a story-share message's referenced story exists.
 */
@Repository
public interface MessageStoryRepository extends JpaRepository<Story, UUID> {}
