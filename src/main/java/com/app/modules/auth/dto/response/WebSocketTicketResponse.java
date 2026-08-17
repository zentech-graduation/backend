package com.app.modules.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** Single-use ticket authenticating one WebSocket handshake. */
@Schema(description = "Single-use ticket authenticating one WebSocket handshake")
public record WebSocketTicketResponse(
        @Schema(
                        description =
                                "Opaque single-use ticket. Supply it as the handshake query"
                                        + " parameter named ticket. Expires 30 seconds after issue"
                                        + " and cannot be redeemed twice.",
                        example =
                                "9f2c4a1e8b7d6c5f4e3d2c1b0a998877665544332211ffeeddccbbaa99887766")
                String ticket) {}
