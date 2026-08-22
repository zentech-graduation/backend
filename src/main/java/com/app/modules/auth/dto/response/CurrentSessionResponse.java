package com.app.modules.auth.dto.response;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Identifies which session the calling client is using.
 *
 * <p>Exists because a session is not identifiable from an access token. The token's {@code jti} is
 * unique per access token and carries no link to the {@code refresh_tokens} row that is the
 * session, and the refresh cookie is scoped to the auth path so it never reaches the administrative
 * tree where the session listing lives. A reviewer reading its own sessions therefore cannot tell
 * which row it is sitting on without asking here.
 *
 * @param sessionId the caller's session, matching an {@code id} in the administrative session
 *     listing; null when the request carried no usable refresh token, which is the ordinary case
 *     for a client that holds its refresh token somewhere the cookie path does not cover
 */
@Schema(description = "Which session the calling client is using")
public record CurrentSessionResponse(
        @Schema(
                        description =
                                "The caller's session identifier, matching an id in the"
                                        + " administrative session listing. Null when the request"
                                        + " carried no usable refresh token.",
                        nullable = true)
                UUID sessionId) {}
