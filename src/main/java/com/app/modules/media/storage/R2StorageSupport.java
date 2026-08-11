package com.app.modules.media.storage;

import org.springframework.util.StringUtils;

import com.app.common.enums.ApiErrorCode;
import com.app.common.exception.AppException;
import com.app.modules.media.config.MediaProperties;

/** Shared R2 configuration guard for the presign and metadata storage services. */
final class R2StorageSupport {

    private R2StorageSupport() {}

    /**
     * Returns the R2 settings only when every credential and location value is present.
     *
     * <p>Both storage services call this before touching the SDK so an unconfigured deployment
     * fails with a typed 503 rather than an SDK-level error the caller cannot interpret.
     */
    static MediaProperties.R2 validatedR2Properties(MediaProperties mediaProperties) {
        MediaProperties.R2 r2 = mediaProperties.getR2();
        if (r2 == null
                || !StringUtils.hasText(r2.getEndpoint())
                || !StringUtils.hasText(r2.getAccessKeyId())
                || !StringUtils.hasText(r2.getSecretAccessKey())
                || !StringUtils.hasText(r2.getBucket())
                || !StringUtils.hasText(r2.getRegion())) {
            throw new AppException(ApiErrorCode.MEDIA_STORAGE_NOT_CONFIGURED);
        }
        return r2;
    }
}
