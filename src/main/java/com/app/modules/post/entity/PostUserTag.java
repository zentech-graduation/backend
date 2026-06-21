package com.app.modules.post.entity;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * User tagged within a post image, with optional pixel-percentage coordinates.
 *
 * <p>Maps to {@code post_user_tags}. The table has no {@code created_at} column. Tag write flows
 * are out of Phase A scope; this mapping exists for schema completeness.
 */
@Entity
@Table(name = "post_user_tags")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PostUserTag {

    @EmbeddedId private PostUserTagId id;

    @Column(name = "media_asset_id")
    private UUID mediaAssetId;

    @Column(name = "x_position", precision = 5, scale = 2)
    private BigDecimal xPosition;

    @Column(name = "y_position", precision = 5, scale = 2)
    private BigDecimal yPosition;
}
