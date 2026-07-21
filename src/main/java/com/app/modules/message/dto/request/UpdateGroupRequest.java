package com.app.modules.message.dto.request;

import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/** Request body for renaming a group or changing its avatar. Both fields are optional. */
@Schema(description = "Group metadata update payload")
public record UpdateGroupRequest(
        @Schema(description = "New group display name.") @Size(max = 100) String groupName,
        @Schema(description = "New group avatar CDN URL.") @Size(max = 512)
                String groupAvatarUrl) {}
