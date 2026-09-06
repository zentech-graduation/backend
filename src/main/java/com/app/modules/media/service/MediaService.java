package com.app.modules.media.service;

import com.app.modules.media.dto.request.MediaUploadCompleteRequest;
import com.app.modules.media.dto.request.MediaUploadUrlRequest;
import com.app.modules.media.dto.response.MediaAssetResponse;
import com.app.modules.media.dto.response.MediaConstraintsResponse;
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

    /**
     * Returns the limits the upload path enforces, so a client never hardcodes them.
     *
     * <p>Read from the same allowlist accessor and the same settings the validator consults. The
     * published values and the enforced values cannot diverge because there is only one of each.
     *
     * @return accepted MIME types, the size ceiling, and the advisory video duration ceiling
     */
    MediaConstraintsResponse getUploadConstraints();
}
