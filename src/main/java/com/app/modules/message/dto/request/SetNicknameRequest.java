package com.app.modules.message.dto.request;

import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/** Request body for setting or clearing the caller's private label for a conversation. */
@Schema(description = "Conversation nickname payload")
public record SetNicknameRequest(
        @Schema(description = "The label to show, or null/blank to clear it.", nullable = true)
                @Size(max = 50)
                String nickname) {}
