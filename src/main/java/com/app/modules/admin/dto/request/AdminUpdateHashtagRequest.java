package com.app.modules.admin.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.app.modules.hashtag.enums.HashtagStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Lifecycle transition for one existing hashtag.
 *
 * <p>Carries no {@code name} field, which is how the name is made immutable. A hashtag's name is
 * its identity: every {@code post_hashtags} row, every caption that produced one, and every indexed
 * document is keyed on it, so renaming would silently reattribute other people's posts to a term
 * they never wrote. Because request DTOs reject unrecognised properties, a body carrying {@code
 * name} fails deserialization rather than being quietly discarded, which would leave the caller
 * believing the rename happened. This is the same technique {@code UpdateProfileRequest} uses to
 * make an account's email unchangeable.
 */
@JsonIgnoreProperties(ignoreUnknown = false)
@Schema(description = "Lifecycle transition for one hashtag")
public record AdminUpdateHashtagRequest(
        @Schema(
                        description =
                                "State to move the hashtag to; must differ from its current one",
                        example = "banned",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotNull
                HashtagStatus status,
        @Schema(
                        description = "Justification recorded on the hashtag row and the audit row",
                        example = "Coordinated harassment campaign",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotBlank
                @Size(max = 2000)
                String note) {}
