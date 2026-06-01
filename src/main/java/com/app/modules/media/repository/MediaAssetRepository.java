package com.app.modules.media.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.app.modules.media.entity.MediaAsset;

@Repository
public interface MediaAssetRepository
        extends JpaRepository<MediaAsset, UUID>, MediaAssetInsertRepository {

    boolean existsByStorageKey(String storageKey);
}
