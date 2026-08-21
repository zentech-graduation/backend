package com.app.modules.admin.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * One live session of an account.
 *
 * <p>A session is live when its refresh token is neither revoked nor past its expiry. The refresh
 * token value itself is never exposed; only its row identifier and device metadata are.
 */
@Schema(description = "One live session of an account")
public record AdminUserSessionResponse(
        @Schema(description = "Refresh-token row identifier") UUID id,
        @Schema(description = "Opaque device identifier supplied at issuance", nullable = true)
                String deviceId,
        @Schema(description = "User agent recorded at issuance", nullable = true) String userAgent,
        @Schema(description = "Client IP recorded at issuance", nullable = true) String ipAddress,
        @Schema(description = "Session start timestamp") OffsetDateTime createdAt,
        @Schema(description = "Session expiry timestamp") OffsetDateTime expiresAt) {}
