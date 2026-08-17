package com.app.modules.auth.service;

/**
 * Issues and redeems single-use tickets that authenticate one WebSocket handshake.
 *
 * <p>A browser cannot set an Authorization header on a WebSocket upgrade, so a credential has to
 * travel in the handshake URL. Query strings are logged by default by nginx, most CDNs, and cloud
 * load balancers, so sending the access token there leaves valid credentials sitting in log storage
 * far longer than their own lifetime. A ticket is single-use, expires in seconds, and is worthless
 * once redeemed.
 *
 * <p>A ticket resolves to the caller's access token rather than to a user id, so the handshake
 * still ends up holding the real token. That is required by {@code
 * WebSocketRevocationSweepService}, which re-validates a live session by resolving the token
 * recorded when the session connected. Resolving to a user id would leave the sweep with nothing to
 * check and silently undo revocation.
 */
public interface WebSocketTicketService {

    /**
     * Issues a ticket that redeems to the supplied access token.
     *
     * @param accessToken the caller's raw access token, without the {@code Bearer } prefix
     * @return an opaque single-use ticket
     */
    String issueTicket(String accessToken);

    /**
     * Redeems a ticket exactly once and returns the access token it was issued against.
     *
     * @param ticket the opaque ticket supplied on the handshake
     * @return the access token stored when the ticket was issued
     * @throws com.app.common.exception.AppException if the ticket is unknown, expired, or already
     *     redeemed
     */
    String consumeTicket(String ticket);
}
