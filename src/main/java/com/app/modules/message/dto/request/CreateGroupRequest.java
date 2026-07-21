package com.app.modules.message.dto.request;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/** Request body for creating a group conversation. */
@Schema(description = "Group conversation creation payload")
public record CreateGroupRequest(
        @Schema(description = "Group display name.") @NotBlank @Size(max = 100) String groupName,
        @Schema(description = "Optional group avatar CDN URL.") @Size(max = 512)
                String groupAvatarUrl,
        @Schema(description = "Other members to add at creation; the caller is added as admin.")
                @NotEmpty
                List<UUID> participantIds) {}
