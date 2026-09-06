package com.app.modules.admin.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Justification for taking a hashtag out of the registry.
 *
 * <p>The deletion is a status change, never a row removal. Removing the row would cascade to {@code
 * post_hashtags} and drive the {@code post_count} trigger over every post that used the tag,
 * destroying history nothing asked to destroy and leaving no way back.
 */
@JsonIgnoreProperties(ignoreUnknown = false)
@Schema(description = "Justification for deleting a hashtag")
public record AdminDeleteHashtagRequest(
        @Schema(
                        description = "Justification recorded on the hashtag row and the audit row",
                        example = "Duplicate of an existing tag, created by a typo",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotBlank
                @Size(max = 2000)
                String reason) {}
