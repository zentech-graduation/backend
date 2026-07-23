package com.app.modules.message.service;

import java.util.List;
import java.util.UUID;

import com.app.common.response.CursorPageResponse;
import com.app.modules.message.dto.request.AddParticipantsRequest;
import com.app.modules.message.dto.request.CreateDirectConversationRequest;
import com.app.modules.message.dto.request.CreateGroupRequest;
import com.app.modules.message.dto.request.UpdateGroupRequest;
import com.app.modules.message.dto.response.ConversationResponse;
import com.app.modules.message.dto.response.ConversationSummaryResponse;
import com.app.modules.message.dto.response.ParticipantResponse;

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
     *     self-conversation; USER_NOT_FOUND when the target does not exist; SOCIAL_BLOCKED on a
     *     block in either direction; MESSAGE_REQUEST_NOT_ALLOWED when the target restricts message
     *     requests and does not already follow the caller
     */
    ConversationResponse createDirectConversation(
            UUID actorId, CreateDirectConversationRequest request);

    /**
     * Creates a group conversation with the caller as its first admin.
     *
     * @param actorId the authenticated creator
     * @param request group name, optional avatar, and initial member ids
     * @return the created conversation
     * @throws com.app.common.exception.AppException GROUP_CHAT_DISABLED when the feature toggle is
     *     off; CONVERSATION_INVALID_PARTICIPANTS for an empty, oversized, self-including, or
     *     blocked-member list; USER_NOT_FOUND when a member does not exist
     */
    ConversationResponse createGroupConversation(UUID actorId, CreateGroupRequest request);

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
     * Lists a conversation's active and former members.
     *
     * @param actorId the authenticated caller
     * @param conversationId the conversation to inspect
     * @return the member list, oldest join first
     * @throws com.app.common.exception.AppException CONVERSATION_NOT_FOUND when missing;
     *     CONVERSATION_FORBIDDEN when the caller is not an active participant
     */
    List<ParticipantResponse> listParticipants(UUID actorId, UUID conversationId);

    /**
     * Adds members to a group conversation. Already-active members are skipped; a member who had
     * left is reactivated.
     *
     * @param actorId the authenticated caller, who must be an active group admin
     * @param conversationId the group to modify
     * @param request user ids to add
     * @throws com.app.common.exception.AppException CONVERSATION_NOT_FOUND when missing;
     *     CONVERSATION_NOT_GROUP for a 1-1 conversation; GROUP_ADMIN_REQUIRED when the caller is
     *     not an active admin; CONVERSATION_INVALID_PARTICIPANTS when the resulting size would
     *     exceed the configured maximum or a target is blocked with the caller; USER_NOT_FOUND when
     *     a target does not exist
     */
    void addParticipants(UUID actorId, UUID conversationId, AddParticipantsRequest request);

    /**
     * Removes a member from a group conversation by setting their {@code left_at}; membership
     * history is preserved, never hard-deleted.
     *
     * @param actorId the authenticated caller, who must be an active group admin
     * @param conversationId the group to modify
     * @param targetUserId the member to remove
     * @throws com.app.common.exception.AppException CONVERSATION_NOT_FOUND when missing;
     *     CONVERSATION_NOT_GROUP for a 1-1 conversation; GROUP_ADMIN_REQUIRED when the caller is
     *     not an active admin; PARTICIPANT_NOT_FOUND when the target is not an active member
     */
    void removeParticipant(UUID actorId, UUID conversationId, UUID targetUserId);

    /**
     * Leaves a conversation by setting the caller's own {@code left_at}. Idempotent - leaving again
     * is a no-op. If the caller was a group's last active admin, the oldest remaining active member
     * is promoted to admin so the group is never left without one.
     *
     * @param actorId the authenticated caller
     * @param conversationId the conversation to leave
     * @throws com.app.common.exception.AppException CONVERSATION_NOT_FOUND when missing;
     *     CONVERSATION_FORBIDDEN when the caller was never a participant
     */
    void leaveConversation(UUID actorId, UUID conversationId);

    /**
     * Renames a group and/or changes its avatar. Fields left null in the request are unchanged.
     *
     * @param actorId the authenticated caller, who must be an active group admin
     * @param conversationId the group to modify
     * @param request the fields to update
     * @return the updated conversation detail
     * @throws com.app.common.exception.AppException CONVERSATION_NOT_FOUND when missing;
     *     CONVERSATION_NOT_GROUP for a 1-1 conversation; GROUP_ADMIN_REQUIRED when the caller is
     *     not an active admin
     */
    ConversationResponse updateGroup(UUID actorId, UUID conversationId, UpdateGroupRequest request);
}
