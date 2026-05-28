package com.app.modules.media.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.app.modules.media.converter.MediaTypeConverter;
import com.app.modules.media.enums.MediaType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Canonical metadata row for a media object already uploaded to object storage. */
@Entity
@Table(name = "media_assets")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MediaAsset {

    @Id
    @Column(name = "id", nullable = false, insertable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "storage_key", nullable = false, unique = true, columnDefinition = "TEXT")
    private String storageKey;

    @Column(name = "cdn_url", nullable = false, columnDefinition = "TEXT")
    private String cdnUrl;

    @Convert(converter = MediaTypeConverter.class)
    @Column(name = "media_type", nullable = false, columnDefinition = "media_type")
    private MediaType mediaType;

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Column(name = "width")
    private Integer width;

    @Column(name = "height")
    private Integer height;

    @Column(name = "duration")
    private Integer duration;

    @Column(name = "blurhash", length = 100)
    private String blurhash;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private OffsetDateTime createdAt;
}
