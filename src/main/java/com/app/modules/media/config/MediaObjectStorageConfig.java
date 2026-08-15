package com.app.modules.media.config;

import java.net.URI;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

/** Object storage client wiring for media upload verification. */
@Configuration
public class MediaObjectStorageConfig {

    /**
     * Builds the single S3 client used to verify uploaded objects against R2.
     *
     * <p>Deliberately a singleton, unlike the per-call {@code S3Presigner} in {@code
     * R2ObjectStoragePresignService}. A presigner performs no I/O and is cheap to construct, but
     * this client issues a real network round trip on the hot path of every upload confirmation, so
     * rebuilding it per call would discard connection pooling and TLS session reuse.
     *
     * <p>Lazy so that a deployment without R2 credentials still starts; the metadata service
     * rejects with a typed error before the client is ever resolved.
     */
    @Bean
    @Lazy
    public S3Client mediaObjectStorageClient(MediaProperties mediaProperties) {
        MediaProperties.R2 r2 = mediaProperties.getR2();
        return S3Client.builder()
                .endpointOverride(URI.create(r2.getEndpoint().trim()))
                .region(Region.of(r2.getRegion().trim()))
                .credentialsProvider(
                        StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(
                                        r2.getAccessKeyId().trim(), r2.getSecretAccessKey())))
                .serviceConfiguration(
                        S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .httpClientBuilder(
                        ApacheHttpClient.builder()
                                .connectionTimeout(r2.getConnectTimeout())
                                .socketTimeout(r2.getReadTimeout()))
                .overrideConfiguration(
                        ClientOverrideConfiguration.builder()
                                .apiCallTimeout(r2.getApiCallTimeout())
                                .build())
                .build();
    }
}
