package com.app.modules.message.service.impl;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.app.common.enums.ApiErrorCode;
import com.app.common.exception.AppException;
import com.app.common.pagination.Cursor;
import com.app.common.pagination.CursorCodec;
import com.app.common.pagination.CursorScope;
import com.app.common.pagination.TimeCursors;
import com.app.common.response.CursorPageResponse;
import com.app.modules.message.config.MessageProperties;
import com.app.modules.message.dto.request.AddParticipantsRequest;
import com.app.modules.message.dto.request.CreateDirectConversationRequest;
import com.app.modules.message.dto.request.CreateGroupRequest;
import com.app.modules.message.dto.request.UpdateGroupRequest;
import com.app.modules.message.dto.response.ConversationResponse;
import com.app.modules.message.dto.response.ConversationSummaryResponse;
import com.app.modules.message.dto.response.MessageResponse;
import com.app.modules.message.dto.response.ParticipantResponse;
import com.app.modules.message.entity.Conversation;
import com.app.modules.message.entity.ConversationParticipant;
import com.app.modules.message.entity.ConversationParticipantId;
import com.app.modules.message.entity.Message;
import com.app.modules.message.mapper.MessageMapper;
import com.app.modules.message.repository.ConversationParticipantRepository;
import com.app.modules.message.repository.ConversationRepository;
import com.app.modules.message.repository.ConversationUnreadCount;
import com.app.modules.message.repository.MessageRepository;
import com.app.modules.message.repository.MessageUserRepository;
import com.app.modules.message.repository.MessageUserSettingsRepository;
import com.app.modules.message.service.ConversationService;
import com.app.modules.social.service.SocialService;
import com.app.modules.users.entity.User;
import com.app.modules.users.entity.UserSettings;

@Service
public class ConversationServiceImpl implements ConversationService {

    private static final Logger log = LoggerFactory.getLogger(ConversationServiceImpl.class);

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final MessageRepository messageRepository;
    private final MessageUserRepository userRepository;
    private final MessageUserSettingsRepository userSettingsRepository;
    private final SocialService socialService;
    private final MessageProperties properties;
    private final MessageMapper mapper;

    public ConversationServiceImpl(
            ConversationRepository conversationRepository,
            ConversationParticipantRepository participantRepository,
            MessageRepository messageRepository,
            MessageUserRepository userRepository,
            MessageUserSettingsRepository userSettingsRepository,
            SocialService socialService,
            MessageProperties properties,
            MessageMapper mapper) {
        this.conversationRepository = conversationRepository;
        this.participantRepository = participantRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
        this.userSettingsRepository = userSettingsRepository;
        this.socialService = socialService;
        this.properties = properties;
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public ConversationResponse createDirectConversation(
            UUID actorId, CreateDirectConversationRequest request) {
        UUID targetId = request.targetUserId();
        if (actorId.equals(targetId)) {
            throw new AppException(
                    ApiErrorCode.CONVERSATION_INVALID_PARTICIPANTS, "Cannot message yourself");
        }
        userRepository
                .findByIdAndDeletedAtIsNull(targetId)
                .orElseThrow(() -> new AppException(ApiErrorCode.USER_NOT_FOUND));
        assertNotBlocked(actorId, targetId);
        assertMessageRequestAllowed(actorId, targetId);

        // Serializes concurrent createDirectConversation calls for this pair so the
        // check-then-create
        // below cannot race into two conversations; the lock is released automatically at
        // transaction
        // end, and the returned key is reused on insert as a database-level uniqueness backstop.
        String pairKey = conversationRepository.lockDirectConversationPair(actorId, targetId);

        Optional<Conversation> existing =
                conversationRepository.findDirectConversationBetween(actorId, targetId);
        Conversation conversation;
        if (existing.isPresent()) {
            conversation = existing.get();
            // Reactivate the caller's own membership if they had previously left; the other
            // participant's state is untouched.
            ConversationParticipant actorParticipant =
                    participantRepository
                            .findByIdConversationIdAndIdUserId(conversation.getId(), actorId)
                            .orElseThrow(
                                    () -> new AppException(ApiErrorCode.CONVERSATION_NOT_FOUND));
            if (actorParticipant.getLeftAt() != null) {
                actorParticipant.setLeftAt(null);
                participantRepository.save(actorParticipant);
            }
        } else {
            conversation =
                    Conversation.builder()
                            .isGroup(false)
                            .createdBy(actorId)
                            .directPairKey(pairKey)
                            .build();
            conversationRepository.saveAndFlush(conversation);
            participantRepository.save(newParticipant(conversation.getId(), actorId, false));
            participantRepository.save(newParticipant(conversation.getId(), targetId, false));
            log.info("Direct conversation created: conversationId={}", conversation.getId());
        }
        return assembleDetail(conversation);
    }

    @Override
    @Transactional
    public ConversationResponse createGroupConversation(UUID actorId, CreateGroupRequest request) {
        if (!properties.groupChatEnabled()) {
            throw new AppException(ApiErrorCode.GROUP_CHAT_DISABLED);
        }
        List<UUID> memberIds =
                request.participantIds().stream()
                        .distinct()
                        .filter(id -> !id.equals(actorId))
                        .toList();
        if (memberIds.isEmpty()) {
            throw new AppException(
                    ApiErrorCode.CONVERSATION_INVALID_PARTICIPANTS,
                    "At least one other participant is required");
        }
        // +1 accounts for the creator, who is always a member alongside the requested list.
        if (memberIds.size() + 1 > properties.maxGroupParticipants()) {
            throw new AppException(
                    ApiErrorCode.CONVERSATION_INVALID_PARTICIPANTS,
                    "Too many participants for a single group");
        }
        List<User> members = userRepository.findAllByIdInAndDeletedAtIsNull(memberIds);
        if (members.size() != memberIds.size()) {
            throw new AppException(ApiErrorCode.USER_NOT_FOUND);
        }
        for (UUID memberId : memberIds) {
            assertNotBlocked(actorId, memberId);
        }

        Conversation conversation =
                Conversation.builder()
                        .isGroup(true)
                        .groupName(request.groupName())
                        .groupAvatarUrl(request.groupAvatarUrl())
                        .createdBy(actorId)
                        .build();
        conversationRepository.saveAndFlush(conversation);
        participantRepository.save(newParticipant(conversation.getId(), actorId, true));
        for (UUID memberId : memberIds) {
            participantRepository.save(newParticipant(conversation.getId(), memberId, false));
        }
        log.info(
                "Group conversation created: conversationId={}, members={}",
                conversation.getId(),
                memberIds.size() + 1);
        return assembleDetail(conversation);
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPageResponse<ConversationSummaryResponse> listMyConversations(
            UUID actorId, String cursor, int limit) {
        int pageSize = normalizeLimit(limit);
        ConversationCursor decoded = decodeCursor(cursor);
        PageRequest page = PageRequest.of(0, pageSize + 1);
        List<Conversation> conversations =
                decoded.isEmpty()
                        ? conversationRepository.findFirstMyConversations(actorId, page)
                        : conversationRepository.findMyConversationsBefore(
                                actorId, decoded.lastMessageAt(), decoded.conversationId(), page);
        boolean hasNextPage = conversations.size() > pageSize;
        if (hasNextPage) {
            conversations = conversations.subList(0, pageSize);
        }
        if (conversations.isEmpty()) {
            return CursorPageResponse.of(
                    Collections.emptyList(), false, null, null, cursor != null);
        }

        List<UUID> conversationIds = conversations.stream().map(Conversation::getId).toList();
        Map<UUID, List<ParticipantResponse>> participantsByConversation =
                batchAssembleActiveParticipants(conversationIds);
        Map<UUID, Long> unreadByConversation =
                messageRepository.countUnreadPerConversation(actorId, conversationIds).stream()
                        .collect(
                                Collectors.toMap(
                                        ConversationUnreadCount::getConversationId,
                                        ConversationUnreadCount::getUnreadCount));
        Map<UUID, MessageResponse> lastMessageByConversation =
                messageRepository.findLastMessagePerConversation(conversationIds).stream()
                        .collect(
                                Collectors.toMap(
                                        Message::getConversationId, mapper::toMessageResponse));

        List<ConversationSummaryResponse> content =
                conversations.stream()
                        .map(
                                c ->
                                        mapper.toSummaryResponse(
                                                c,
                                                participantsByConversation.getOrDefault(
                                                        c.getId(), List.of()),
                                                unreadByConversation.getOrDefault(c.getId(), 0L),
                                                lastMessageByConversation.get(c.getId())))
                        .toList();
        String startCursor = encodeCursor(conversations.get(0));
        String endCursor = encodeCursor(conversations.get(conversations.size() - 1));
        return CursorPageResponse.of(content, hasNextPage, startCursor, endCursor, cursor != null);
    }

    @Override
    @Transactional(readOnly = true)
    public ConversationResponse getConversation(UUID actorId, UUID conversationId) {
        Conversation conversation = fetchConversation(conversationId);
        requireActiveParticipant(conversationId, actorId);
        return assembleDetail(conversation);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ParticipantResponse> listParticipants(UUID actorId, UUID conversationId) {
        fetchConversation(conversationId);
        requireActiveParticipant(conversationId, actorId);
        return assembleParticipants(conversationId);
    }

    @Override
    @Transactional
    public void addParticipants(UUID actorId, UUID conversationId, AddParticipantsRequest request) {
        Conversation conversation = fetchConversation(conversationId);
        requireGroupAdmin(conversation, actorId);

        List<UUID> targetIds =
                request.userIds().stream().distinct().filter(id -> !id.equals(actorId)).toList();
        if (targetIds.isEmpty()) {
            throw new AppException(ApiErrorCode.CONVERSATION_INVALID_PARTICIPANTS);
        }
        List<User> targets = userRepository.findAllByIdInAndDeletedAtIsNull(targetIds);
        if (targets.size() != targetIds.size()) {
            throw new AppException(ApiErrorCode.USER_NOT_FOUND);
        }
        for (UUID targetId : targetIds) {
            assertNotBlocked(actorId, targetId);
        }

        List<ConversationParticipant> toReactivate = new ArrayList<>();
        List<UUID> toInsert = new ArrayList<>();
        for (UUID targetId : targetIds) {
            Optional<ConversationParticipant> existing =
                    participantRepository.findByIdConversationIdAndIdUserId(
                            conversationId, targetId);
            if (existing.isEmpty()) {
                toInsert.add(targetId);
            } else if (existing.get().getLeftAt() != null) {
                toReactivate.add(existing.get());
            }
            // An already-active member is a silent no-op.
        }

        int currentActive =
                participantRepository.countByIdConversationIdAndLeftAtIsNull(conversationId);
        int newJoiners = toReactivate.size() + toInsert.size();
        if (currentActive + newJoiners > properties.maxGroupParticipants()) {
            throw new AppException(
                    ApiErrorCode.CONVERSATION_INVALID_PARTICIPANTS,
                    "Too many participants for a single group");
        }

        for (ConversationParticipant participant : toReactivate) {
            participant.setLeftAt(null);
            participantRepository.save(participant);
        }
        for (UUID targetId : toInsert) {
            participantRepository.save(newParticipant(conversationId, targetId, false));
        }
    }

    @Override
    @Transactional
    public void removeParticipant(UUID actorId, UUID conversationId, UUID targetUserId) {
        Conversation conversation = fetchConversation(conversationId);
        requireGroupAdmin(conversation, actorId);
        ConversationParticipant target =
                participantRepository
                        .findByIdConversationIdAndIdUserId(conversationId, targetUserId)
                        .filter(p -> p.getLeftAt() == null)
                        .orElseThrow(() -> new AppException(ApiErrorCode.PARTICIPANT_NOT_FOUND));
        boolean removedAdmin = target.isAdmin();
        target.setLeftAt(OffsetDateTime.now(ZoneOffset.UTC));
        participantRepository.save(target);

        // Mirrors leaveConversation: removing the last active admin (including self-removal
        // through this endpoint) must not leave the group permanently unmanageable.
        if (removedAdmin) {
            promoteReplacementAdminIfNeeded(conversationId);
        }
    }

    @Override
    @Transactional
    public void leaveConversation(UUID actorId, UUID conversationId) {
        Conversation conversation = fetchConversation(conversationId);
        ConversationParticipant actorParticipant =
                participantRepository
                        .findByIdConversationIdAndIdUserId(conversationId, actorId)
                        .orElseThrow(() -> new AppException(ApiErrorCode.CONVERSATION_FORBIDDEN));
        if (actorParticipant.getLeftAt() != null) {
            return;
        }
        actorParticipant.setLeftAt(OffsetDateTime.now(ZoneOffset.UTC));
        participantRepository.save(actorParticipant);

        if (conversation.isGroup() && actorParticipant.isAdmin()) {
            promoteReplacementAdminIfNeeded(conversationId);
        }
    }

    @Override
    @Transactional
    public ConversationResponse updateGroup(
            UUID actorId, UUID conversationId, UpdateGroupRequest request) {
        Conversation conversation = fetchConversation(conversationId);
        requireGroupAdmin(conversation, actorId);
        if (request.groupName() != null) {
            conversation.setGroupName(request.groupName());
        }
        if (request.groupAvatarUrl() != null) {
            conversation.setGroupAvatarUrl(request.groupAvatarUrl());
        }
        conversationRepository.save(conversation);
        return assembleDetail(conversation);
    }

    private void promoteReplacementAdminIfNeeded(UUID conversationId) {
        List<ConversationParticipant> active =
                participantRepository
                        .findByIdConversationIdOrderByJoinedAtAsc(conversationId)
                        .stream()
                        .filter(p -> p.getLeftAt() == null)
                        .toList();
        boolean hasAdmin = active.stream().anyMatch(ConversationParticipant::isAdmin);
        if (!hasAdmin && !active.isEmpty()) {
            ConversationParticipant replacement = active.get(0);
            replacement.setAdmin(true);
            participantRepository.save(replacement);
            log.info(
                    "Promoted new group admin: conversationId={}, userId={}",
                    conversationId,
                    replacement.getId().getUserId());
        }
    }

    // Stealth block model: matches assemblePublicProfile's reference behaviour - a block in
    // either direction must be indistinguishable from userB not existing, not a status that
    // confirms a block relationship. Shared by direct-conversation creation, group creation, and
    // adding a participant, so a blocked target looks the same across all three.
    private void assertNotBlocked(UUID userA, UUID userB) {
        if (socialService.isBlockedBetween(userA, userB)) {
            throw new AppException(ApiErrorCode.NOT_FOUND, "Target user not found");
        }
    }

    /**
     * A target with {@code allowMessageRequests=false} only accepts new conversations from users
     * they already follow back; this is the "non-follower" gate the setting is meant to enforce.
     */
    private void assertMessageRequestAllowed(UUID actorId, UUID targetId) {
        boolean allowRequests =
                userSettingsRepository
                        .findById(targetId)
                        .map(UserSettings::isAllowMessageRequests)
                        .orElse(true);
        if (!allowRequests && !socialService.hasAcceptedFollow(targetId, actorId)) {
            throw new AppException(ApiErrorCode.MESSAGE_REQUEST_NOT_ALLOWED);
        }
    }

    private Conversation fetchConversation(UUID conversationId) {
        return conversationRepository
                .findById(conversationId)
                .orElseThrow(() -> new AppException(ApiErrorCode.CONVERSATION_NOT_FOUND));
    }

    private void requireActiveParticipant(UUID conversationId, UUID userId) {
        if (!participantRepository.existsByIdConversationIdAndIdUserIdAndLeftAtIsNull(
                conversationId, userId)) {
            throw new AppException(ApiErrorCode.CONVERSATION_FORBIDDEN);
        }
    }

    private void requireGroupAdmin(Conversation conversation, UUID actorId) {
        if (!conversation.isGroup()) {
            throw new AppException(ApiErrorCode.CONVERSATION_NOT_GROUP);
        }
        boolean isActiveAdmin =
                participantRepository
                        .findByIdConversationIdAndIdUserId(conversation.getId(), actorId)
                        .filter(p -> p.getLeftAt() == null && p.isAdmin())
                        .isPresent();
        if (!isActiveAdmin) {
            throw new AppException(ApiErrorCode.GROUP_ADMIN_REQUIRED);
        }
    }

    private static ConversationParticipant newParticipant(
            UUID conversationId, UUID userId, boolean isAdmin) {
        return ConversationParticipant.builder()
                .id(new ConversationParticipantId(conversationId, userId))
                .isAdmin(isAdmin)
                .build();
    }

    private ConversationResponse assembleDetail(Conversation conversation) {
        return mapper.toConversationResponse(
                conversation, assembleParticipants(conversation.getId()));
    }

    private List<ParticipantResponse> assembleParticipants(UUID conversationId) {
        List<ConversationParticipant> participants =
                participantRepository.findByIdConversationIdOrderByJoinedAtAsc(conversationId);
        Map<UUID, User> users = hydrateUsers(participants);
        return participants.stream()
                .map(p -> mapper.toParticipantResponse(p, users.get(p.getId().getUserId())))
                .toList();
    }

    private Map<UUID, List<ParticipantResponse>> batchAssembleActiveParticipants(
            List<UUID> conversationIds) {
        List<ConversationParticipant> participants =
                participantRepository.findByIdConversationIdInAndLeftAtIsNull(conversationIds);
        Map<UUID, User> users = hydrateUsers(participants);
        Map<UUID, List<ParticipantResponse>> result = new LinkedHashMap<>();
        for (ConversationParticipant participant : participants) {
            result.computeIfAbsent(participant.getId().getConversationId(), k -> new ArrayList<>())
                    .add(
                            mapper.toParticipantResponse(
                                    participant, users.get(participant.getId().getUserId())));
        }
        return result;
    }

    private Map<UUID, User> hydrateUsers(List<ConversationParticipant> participants) {
        Set<UUID> userIds =
                participants.stream()
                        .map(p -> p.getId().getUserId())
                        .collect(Collectors.toCollection(HashSet::new));
        return userIds.isEmpty()
                ? Map.of()
                : userRepository.findAllByIdInAndDeletedAtIsNull(userIds).stream()
                        .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    private int normalizeLimit(int limit) {
        return limit > MAX_PAGE_SIZE ? MAX_PAGE_SIZE : (limit < 1 ? DEFAULT_PAGE_SIZE : limit);
    }

    // The conversation list orders by last_message_at, a mutable key that advances whenever a new
    // message arrives, so a conversation can shift across a page boundary and be seen twice or
    // missed. That instability is intrinsic to any most-recently-active ordering and is accepted;
    // the (last_message_at, id) tuple only breaks exact ties, not the moving key.
    //
    // last_message_at is nullable (NULLS LAST group for a conversation with no message yet), which
    // the shared Cursor's sortValueMicros cannot represent directly since it is a primitive long.
    // NULL_LAST_MESSAGE_SENTINEL stands in for it instead of a bespoke hand-rolled codec: the
    // server is the only issuer of valid cursors and never assigns a real conversation's
    // last_message_at to exactly Long.MIN_VALUE, so the sentinel is unambiguous, and reusing
    // CursorCodec/TimeCursors bounds the decoded value to a long's range - closing the defect a
    // free-text OffsetDateTime.parse had, where a forged out-of-range year overflowed at JDBC
    // bind time instead of failing cleanly at decode time.
    private static final long NULL_LAST_MESSAGE_SENTINEL = Long.MIN_VALUE;

    private String encodeCursor(Conversation conversation) {
        long sortValue =
                conversation.getLastMessageAt() != null
                        ? TimeCursors.toMicros(conversation.getLastMessageAt())
                        : NULL_LAST_MESSAGE_SENTINEL;
        return CursorCodec.encode(
                new Cursor(sortValue, conversation.getId()), CursorScope.CONVERSATIONS);
    }

    private ConversationCursor decodeCursor(String cursor) {
        Cursor decoded = CursorCodec.decode(cursor, CursorScope.CONVERSATIONS);
        if (decoded == null) {
            return new ConversationCursor(null, null);
        }
        OffsetDateTime lastMessageAt =
                decoded.sortValueMicros() == NULL_LAST_MESSAGE_SENTINEL
                        ? null
                        : TimeCursors.fromMicros(decoded.sortValueMicros());
        return new ConversationCursor(lastMessageAt, decoded.id());
    }

    private record ConversationCursor(OffsetDateTime lastMessageAt, UUID conversationId) {
        // A decoded cursor always carries a conversationId, even when lastMessageAt is null
        // (continuation within the null group) - so conversationId alone signals "no cursor".
        boolean isEmpty() {
            return conversationId == null;
        }
    }
}
