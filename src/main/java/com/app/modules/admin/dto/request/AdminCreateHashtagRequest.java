package com.app.modules.admin.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.app.modules.hashtag.enums.HashtagStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Creation of a hashtag ahead of any post using it.
 *
 * <p>Two uses, and the status is what separates them: seeding a term before an event so it is
 * already in the registry when traffic arrives, and banning one before it can be used at all.
 */
@JsonIgnoreProperties(ignoreUnknown = false)
@Schema(description = "Creation of a hashtag directly in a chosen lifecycle state")
public record AdminCreateHashtagRequest(
        @Schema(
                        description =
                                "Hashtag name, with or without the leading #; normalized to"
                                        + " lowercase before insert",
                        example = "worldcup",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotBlank
                @Size(max = 101)
                String name,
        @Schema(
                        description =
                                "State to create the hashtag in. 'deleted' is refused: it names a"
                                        + " state nothing can reach the hashtag from",
                        example = "banned",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                HashtagStatus status,
        @Schema(
                        description = "Justification recorded on the hashtag row",
                        example = "Reserved ahead of the tournament",
                        requiredMode = Schema.RequiredMode.REQUIRED)
                @NotBlank
                @Size(max = 2000)
                String note) {}
