package com.app.modules.media.storage;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.app.common.enums.ApiErrorCode;
import com.app.common.exception.AppException;
import com.app.modules.media.config.MediaProperties;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Service
public class R2ObjectStoragePresignService implements ObjectStoragePresignService {

    private static final Duration MIN_TTL = Duration.ofMinutes(1);
    private static final Duration MAX_TTL = Duration.ofMinutes(30);

    private final MediaProperties mediaProperties;

    public R2ObjectStoragePresignService(MediaProperties mediaProperties) {
        this.mediaProperties = mediaProperties;
    }

    @Override
    public PresignedUpload presignPutObject(String storageKey, String mimeType, long fileSize) {
        MediaProperties.R2 r2 = validatedR2Properties();
        PutObjectRequest putObjectRequest =
                PutObjectRequest.builder()
                        .bucket(r2.getBucket().trim())
                        .key(storageKey)
                        .contentType(mimeType)
                        .build();
        PutObjectPresignRequest presignRequest =
                PutObjectPresignRequest.builder()
                        .signatureDuration(validatedTtl(r2.getUploadUrlTtl()))
                        .putObjectRequest(putObjectRequest)
                        .build();

        try (S3Presigner presigner = buildPresigner(r2)) {
            PresignedPutObjectRequest presignedRequest = presigner.presignPutObject(presignRequest);
            return new PresignedUpload(
                    presignedRequest.url(),
                    "PUT",
                    flattenHeaders(presignedRequest.signedHeaders()),
                    presignedRequest.expiration());
        } catch (RuntimeException ex) {
            throw new AppException(ApiErrorCode.MEDIA_UPLOAD_URL_FAILED);
        }
    }

    private MediaProperties.R2 validatedR2Properties() {
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

    private S3Presigner buildPresigner(MediaProperties.R2 r2) {
        return S3Presigner.builder()
                .endpointOverride(URI.create(r2.getEndpoint().trim()))
                .region(Region.of(r2.getRegion().trim()))
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(
                                        r2.getAccessKeyId().trim(), r2.getSecretAccessKey())))
                .serviceConfiguration(
                        S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }

    private static Duration validatedTtl(Duration ttl) {
        if (ttl == null || ttl.compareTo(MIN_TTL) < 0 || ttl.compareTo(MAX_TTL) > 0) {
            throw new AppException(
                    ApiErrorCode.MEDIA_STORAGE_NOT_CONFIGURED,
                    "Media upload URL TTL must be between 1 and 30 minutes");
        }
        return ttl;
    }

    private static Map<String, String> flattenHeaders(Map<String, java.util.List<String>> headers) {
        return headers.entrySet().stream()
                .filter(entry -> !"host".equalsIgnoreCase(entry.getKey()))
                .collect(
                        Collectors.toUnmodifiableMap(
                                Map.Entry::getKey, entry -> String.join(",", entry.getValue())));
    }
}
