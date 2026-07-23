package com.app.modules.message.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

import io.swagger.v3.oas.annotations.media.Schema;

/** Request body for starting (or reusing) a 1-1 conversation with another user. */
@Schema(description = "1-1 conversation creation payload")
public record CreateDirectConversationRequest(
        @Schema(description = "The other participant.") @NotNull UUID targetUserId) {}
