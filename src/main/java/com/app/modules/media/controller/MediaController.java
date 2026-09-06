package com.app.modules.media.controller;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.app.common.ApiConstants;
import com.app.common.base.BaseController;
import com.app.common.enums.ApiSuccessCode;
import com.app.common.response.ApiResponse;
import com.app.modules.media.api.MediaApi;
import com.app.modules.media.dto.request.MediaUploadCompleteRequest;
import com.app.modules.media.dto.request.MediaUploadUrlRequest;
import com.app.modules.media.dto.response.MediaAssetResponse;
import com.app.modules.media.dto.response.MediaConstraintsResponse;
import com.app.modules.media.dto.response.MediaUploadUrlResponse;
import com.app.modules.media.service.MediaService;

import io.github.resilience4j.ratelimiter.annotation.RateLimiter;

/** REST endpoints for direct media upload operations. */
@RestController
public class MediaController extends BaseController implements MediaApi {

    private final MediaService mediaService;

    public MediaController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    /** Creates a pre-signed direct-upload URL for the authenticated user. */
    @Override
    @PostMapping(ApiConstants.Media.UPLOAD)
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    public ResponseEntity<ApiResponse<MediaUploadUrlResponse>> createUploadUrl(
            @Valid @RequestBody MediaUploadUrlRequest request) {
        return ResponseEntity.ok(
                ApiResponse.success(ApiSuccessCode.OK, mediaService.createUploadUrl(request)));
    }

    /** Confirms a direct media upload and creates the canonical media asset row. */
    @Override
    @PostMapping(ApiConstants.Media.UPLOAD_COMPLETE)
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    public ResponseEntity<ApiResponse<MediaAssetResponse>> completeUpload(
            @Valid @RequestBody MediaUploadCompleteRequest request) {
        MediaAssetResponse response = mediaService.completeUpload(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(ApiSuccessCode.CREATED, response));
    }

    /** Returns the upload constraints the media validator enforces, for authenticated clients. */
    @Override
    @GetMapping(ApiConstants.Media.CONSTRAINTS)
    @RateLimiter(name = "mediumTraffic", fallbackMethod = "rateLimit")
    public ResponseEntity<ApiResponse<MediaConstraintsResponse>> getUploadConstraints() {
        return ResponseEntity.ok(
                ApiResponse.success(ApiSuccessCode.OK, mediaService.getUploadConstraints()));
    }
}
