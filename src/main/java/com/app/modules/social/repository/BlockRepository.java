package com.app.modules.social.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.app.modules.social.entity.Block;
import com.app.modules.social.entity.BlockId;

@Repository
public interface BlockRepository extends JpaRepository<Block, BlockId> {

    Optional<Block> findById(BlockId id);

    boolean existsById(BlockId id);

    List<Block> findByIdBlockerId(java.util.UUID blockerId);

    List<Block> findByIdBlockedId(java.util.UUID blockedId);
}
