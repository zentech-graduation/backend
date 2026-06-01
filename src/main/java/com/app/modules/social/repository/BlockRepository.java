package com.app.modules.social.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.app.modules.social.entity.Block;
import com.app.modules.social.entity.BlockId;

@Repository
public interface BlockRepository extends JpaRepository<Block, BlockId> {
    ///
    Optional<Block> findById(BlockId id);

    boolean existsById(BlockId id);

    List<Block> findByIdBlockerId(UUID blockerId);

    List<Block> findByIdBlockedId(UUID blockedId);

    default boolean existsBetween(UUID firstUserId, UUID secondUserId) {
        return existsById(new BlockId(firstUserId, secondUserId))
                || existsById(new BlockId(secondUserId, firstUserId));
    }
}