package com.app.modules.media.config;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/** Media upload-complete policy values bound from app.media.* configuration. */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.media")
public class MediaProperties {

    private String cdnBaseUrl;

    private List<String> allowedImageMimeTypes =
            List.of("image/jpeg", "image/png", "image/webp", "image/gif");

    private List<String> allowedVideoMimeTypes =
            List.of("video/mp4", "video/webm", "video/quicktime");

    private String storageKeyPattern = "^[a-zA-Z0-9][a-zA-Z0-9/_.-]{1,511}$";

    private R2 r2 = new R2();

    @Getter
    @Setter
    public static class R2 {

        private String endpoint;

        private String accessKeyId;

        private String secretAccessKey;

        private String bucket;

        private String region = "auto";

        private Duration uploadUrlTtl = Duration.ofMinutes(10);

        // Upload confirmation blocks on a HEAD against R2, so these bound how long a storage
        // slowdown can hold an upload-complete request before it fails closed with a 503.
        private Duration connectTimeout = Duration.ofSeconds(2);

        private Duration readTimeout = Duration.ofSeconds(5);

        // Ceiling across all SDK retry attempts; without it the per-attempt timeouts above stack.
        private Duration apiCallTimeout = Duration.ofSeconds(10);
    }
}
