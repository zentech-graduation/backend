package com.app.modules.media.mapper;

import org.springframework.stereotype.Component;

import com.app.modules.media.dto.response.MediaAssetResponse;
import com.app.modules.media.entity.MediaAsset;

@Component
public class MediaAssetMapper {

    public MediaAssetResponse toResponse(MediaAsset asset) {
        return new MediaAssetResponse(
                asset.getId(),
                asset.getUserId(),
                asset.getStorageKey(),
                asset.getCdnUrl(),
                asset.getMediaType(),
                asset.getMimeType(),
                asset.getFileSize(),
                asset.getWidth(),
                asset.getHeight(),
                asset.getDuration(),
                asset.getBlurhash(),
                asset.getCreatedAt());
    }
}
