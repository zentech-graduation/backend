package com.app.modules.message.dto.request;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotEmpty;

import io.swagger.v3.oas.annotations.media.Schema;

/** Request body for adding members to a group conversation. */
@Schema(description = "Group participant addition payload")
public record AddParticipantsRequest(
        @Schema(description = "Users to add as active members.") @NotEmpty List<UUID> userIds) {}
