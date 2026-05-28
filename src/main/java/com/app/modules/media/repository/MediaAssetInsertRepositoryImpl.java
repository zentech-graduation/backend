package com.app.modules.media.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;

import com.app.modules.media.entity.MediaAsset;
import com.app.modules.media.enums.MediaType;

public class MediaAssetInsertRepositoryImpl implements MediaAssetInsertRepository {

    private final JdbcClient jdbcClient;

    public MediaAssetInsertRepositoryImpl(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public MediaAsset insert(MediaAsset mediaAsset) {
        return jdbcClient
                .sql(
                        """
						INSERT INTO media_assets (
							user_id,
							storage_key,
							cdn_url,
							media_type,
							mime_type,
							file_size,
							width,
							height,
							duration,
							blurhash
						)
						VALUES (
							:userId,
							:storageKey,
							:cdnUrl,
							CAST(:mediaType AS media_type),
							:mimeType,
							:fileSize,
							:width,
							:height,
							:duration,
							:blurhash
						)
						RETURNING
							id,
							user_id,
							storage_key,
							cdn_url,
							media_type,
							mime_type,
							file_size,
							width,
							height,
							duration,
							blurhash,
							created_at
						""")
                .param("userId", mediaAsset.getUserId())
                .param("storageKey", mediaAsset.getStorageKey())
                .param("cdnUrl", mediaAsset.getCdnUrl())
                .param("mediaType", mediaAsset.getMediaType().name().toLowerCase())
                .param("mimeType", mediaAsset.getMimeType())
                .param("fileSize", mediaAsset.getFileSize())
                .param("width", mediaAsset.getWidth())
                .param("height", mediaAsset.getHeight())
                .param("duration", mediaAsset.getDuration())
                .param("blurhash", mediaAsset.getBlurhash())
                .query(MediaAssetInsertRepositoryImpl::mapRow)
                .single();
    }

    private static MediaAsset mapRow(ResultSet rs, int rowNum) throws SQLException {
        return MediaAsset.builder()
                .id(rs.getObject("id", UUID.class))
                .userId(rs.getObject("user_id", UUID.class))
                .storageKey(rs.getString("storage_key"))
                .cdnUrl(rs.getString("cdn_url"))
                .mediaType(MediaType.valueOf(rs.getString("media_type").toUpperCase()))
                .mimeType(rs.getString("mime_type"))
                .fileSize(rs.getLong("file_size"))
                .width((Integer) rs.getObject("width"))
                .height((Integer) rs.getObject("height"))
                .duration((Integer) rs.getObject("duration"))
                .blurhash(rs.getString("blurhash"))
                .createdAt(rs.getObject("created_at", java.time.OffsetDateTime.class))
                .build();
    }
}
