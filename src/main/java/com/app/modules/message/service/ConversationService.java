package com.app.modules.message.service;

import java.util.UUID;

import com.app.common.response.CursorPageResponse;
import com.app.modules.message.dto.request.CreateDirectConversationRequest;
import com.app.modules.message.dto.response.ConversationResponse;
import com.app.modules.message.dto.response.ConversationSummaryResponse;

/** Conversation lifecycle: creation, membership, group management, and listing. */
public interface ConversationService {

    /**
     * Starts (or reuses) a 1-1 conversation with the target user.
     *
     * <p>An existing 1-1 between the same two users is reused rather than duplicated; if the caller
     * had previously left it, their participation is reactivated.
     *
     * @param actorId the authenticated caller
     * @param request the target user to converse with
     * @return the conversation, existing or newly created
     * @throws com.app.common.exception.AppException CONVERSATION_INVALID_PARTICIPANTS for a
     *     self-conversation; NOT_FOUND when the target does not exist, or on a block in either
     *     direction - the stealth block model requires the two to be indistinguishable;
     *     MESSAGE_REQUEST_NOT_ALLOWED when the target restricts message requests and does not
     *     already follow the caller
     */
    ConversationResponse createDirectConversation(
            UUID actorId, CreateDirectConversationRequest request);

    /**
     * Lists the caller's active conversations, newest activity first.
     *
     * @param actorId the authenticated caller
     * @param cursor opaque continuation cursor from a previous page, or null for the first page
     * @param limit requested page size, clamped between 1 and 100
     * @return a cursor page of conversation summaries with unread counts
     */
    CursorPageResponse<ConversationSummaryResponse> listMyConversations(
            UUID actorId, String cursor, int limit);

    /**
     * Returns one conversation's detail, including active and former participants.
     *
     * @param actorId the authenticated caller
     * @param conversationId the conversation to fetch
     * @return the conversation detail
     * @throws com.app.common.exception.AppException CONVERSATION_NOT_FOUND when missing;
     *     CONVERSATION_FORBIDDEN when the caller is not an active participant
     */
    ConversationResponse getConversation(UUID actorId, UUID conversationId);

    /**
     * Deletes a conversation for the caller only, by setting their own {@code left_at}. The other
     * participant, the conversation row, and its message history are untouched; a new message from
     * them reactivates the caller's membership, so the conversation reappears the next time it has
     * activity.
     *
     * @param actorId the authenticated caller, who must be an active participant
     * @param conversationId the conversation to leave
     * @throws com.app.common.exception.AppException CONVERSATION_NOT_FOUND when missing;
     *     CONVERSATION_FORBIDDEN when the caller is not an active participant
     */
    void leaveConversation(UUID actorId, UUID conversationId);
}
