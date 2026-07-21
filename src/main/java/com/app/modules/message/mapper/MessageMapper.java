package com.app.modules.message.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.app.modules.message.dto.response.ConversationResponse;
import com.app.modules.message.dto.response.ConversationSummaryResponse;
import com.app.modules.message.dto.response.MessageResponse;
import com.app.modules.message.dto.response.ParticipantResponse;
import com.app.modules.message.entity.Conversation;
import com.app.modules.message.entity.ConversationParticipant;
import com.app.modules.message.entity.Message;
import com.app.modules.users.entity.User;

/** Maps conversation and participant entities to API response DTOs. */
@Mapper(componentModel = "spring")
public interface MessageMapper {

    /**
     * Projects a membership row and its hydrated user onto the participant summary shape.
     *
     * @param participant the membership row
     * @param user the participating user, separately hydrated; display fields are null if the
     *     lookup missed
     * @return the participant summary
     */
    @Mapping(source = "participant.id.userId", target = "userId")
    @Mapping(source = "participant.admin", target = "isAdmin")
    @Mapping(source = "user.username", target = "username")
    @Mapping(source = "user.displayName", target = "displayName")
    @Mapping(source = "user.avatarUrl", target = "avatarUrl")
    ParticipantResponse toParticipantResponse(ConversationParticipant participant, User user);

    /**
     * Builds the conversation detail response from the entity and its already-assembled participant
     * summaries.
     *
     * @param conversation the source conversation
     * @param participants active and former members, already hydrated
     * @return the conversation detail response
     */
    ConversationResponse toConversationResponse(
            Conversation conversation, List<ParticipantResponse> participants);

    /**
     * Builds one conversation-list row from the entity, its active participants, a
     * separately-computed unread count, and its newest message preview.
     *
     * @param conversation the source conversation
     * @param participants active members, already hydrated
     * @param unreadCount unread-message count for the requesting user, computed by the caller
     * @param lastMessage the conversation's newest message, or null if none yet
     * @return the conversation summary response
     */
    @Mapping(source = "conversation.id", target = "id")
    ConversationSummaryResponse toSummaryResponse(
            Conversation conversation,
            List<ParticipantResponse> participants,
            long unreadCount,
            MessageResponse lastMessage);

    /**
     * Projects a message entity onto its API response shape, including a tombstoned one.
     *
     * @param message the source message
     * @return the message response
     */
    @Mapping(source = "deleted", target = "isDeleted")
    MessageResponse toMessageResponse(Message message);
}
