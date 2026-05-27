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

    private List<String> allowedImageMimeTypes = List.of("image/jpeg", "image/png", "image/webp");

    private List<String> allowedVideoMimeTypes = List.of("video/mp4", "video/webm");

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
    }
}
