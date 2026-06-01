package com.app.modules.media.service;

import com.app.modules.media.dto.request.MediaUploadCompleteRequest;
import com.app.modules.media.dto.request.MediaUploadUrlRequest;
import com.app.modules.media.dto.response.MediaAssetResponse;
import com.app.modules.media.dto.response.MediaUploadUrlResponse;

/** Domain API for direct media upload operations. */
public interface MediaService {

    /**
     * Creates a short-lived direct-upload URL for authenticated users.
     *
     * @param request client-declared upload type and size
     * @return pre-signed upload URL and generated storage key
     */
    MediaUploadUrlResponse createUploadUrl(MediaUploadUrlRequest request);

    /**
     * Persists canonical media metadata after the client has uploaded the object to storage.
     *
     * @param request client-submitted metadata for the uploaded object
     * @return persisted media asset metadata
     */
    MediaAssetResponse completeUpload(MediaUploadCompleteRequest request);
}
