package com.app.modules.media.storage;

import java.util.Optional;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import com.app.common.enums.ApiErrorCode;
import com.app.common.exception.AppException;
import com.app.modules.media.config.MediaProperties;

import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Slf4j
@Service
public class R2ObjectStorageMetadataService implements ObjectStorageMetadataService {

    private static final int NOT_FOUND_STATUS = 404;

    private final MediaProperties mediaProperties;
    private final S3Client s3Client;

    public R2ObjectStorageMetadataService(
            MediaProperties mediaProperties, @Lazy S3Client mediaObjectStorageClient) {
        this.mediaProperties = mediaProperties;
        this.s3Client = mediaObjectStorageClient;
    }

    @Override
    public Optional<StoredObjectMetadata> findObjectMetadata(String storageKey) {
        // Validated before the client is resolved so a missing configuration surfaces as a typed
        // error rather than a bean creation failure from the lazy client.
        MediaProperties.R2 r2 = R2StorageSupport.validatedR2Properties(mediaProperties);
        HeadObjectRequest request =
                HeadObjectRequest.builder().bucket(r2.getBucket().trim()).key(storageKey).build();
        try {
            HeadObjectResponse response = s3Client.headObject(request);
            return Optional.of(
                    new StoredObjectMetadata(
                            response.contentLength() == null ? -1L : response.contentLength(),
                            response.contentType()));
        } catch (NoSuchKeyException ex) {
            return Optional.empty();
        } catch (S3Exception ex) {
            // A HEAD carries no response body, so the SDK cannot always decode the error code and
            // reports an absent key as a bare 404 instead of NoSuchKeyException.
            if (ex.statusCode() == NOT_FOUND_STATUS) {
                return Optional.empty();
            }
            log.error("R2 head-object request failed for key {}", storageKey, ex);
            throw new AppException(ApiErrorCode.MEDIA_STORAGE_UNAVAILABLE);
        } catch (RuntimeException ex) {
            log.error("R2 head-object request failed for key {}", storageKey, ex);
            throw new AppException(ApiErrorCode.MEDIA_STORAGE_UNAVAILABLE);
        }
    }
}
