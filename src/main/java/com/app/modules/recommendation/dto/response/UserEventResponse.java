package com.app.modules.recommendation.dto.response;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import com.app.modules.recommendation.enums.UserEventType;

import io.swagger.v3.oas.annotations.media.Schema;

/** One behavioural event as the administrative activity log presents it. */
@Schema(description = "A single recorded behavioural event")
public record UserEventResponse(
        @Schema(
                        description = "Unique event identifier",
                        example = "3fa85f64-5717-4562-b3fc-2c963f66afa6")
                UUID id,
        @Schema(description = "Account the event is attributed to") UUID userId,
        @Schema(description = "What happened") UserEventType eventType,
        @Schema(
                        description = "Kind of thing acted on; null when the event names no target",
                        example = "user",
                        nullable = true)
                String entityType,
        @Schema(
                        description = "Thing acted on; null when the event names no target",
                        nullable = true)
                UUID entityId,
        @Schema(
                        description =
                                "Free-form detail carried with the event. A search carries the term"
                                        + " and the surface it ran against; the other written types"
                                        + " carry nothing.",
                        nullable = true)
                Map<String, Object> metadata,
        @Schema(description = "UTC timestamp when the event was recorded")
                OffsetDateTime createdAt) {}
