package com.app.modules.message.service.impl;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
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
import com.app.common.response.CursorPageResponse;
import com.app.modules.message.config.MessageProperties;
import com.app.modules.message.dto.request.AddParticipantsRequest;
import com.app.modules.message.dto.request.CreateDirectConversationRequest;
import com.app.modules.message.dto.request.CreateGroupRequest;
import com.app.modules.message.dto.request.UpdateGroupRequest;
import com.app.modules.message.dto.response.ConversationResponse;
import com.app.modules.message.dto.response.ConversationSummaryResponse;
import com.app.modules.message.dto.response.ParticipantResponse;
import com.app.modules.message.entity.Conversation;
import com.app.modules.message.entity.ConversationParticipant;
import com.app.modules.message.entity.ConversationParticipantId;
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
            conversation = Conversation.builder().isGroup(false).createdBy(actorId).build();
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
        if (conversations.size() > pageSize) {
            conversations = conversations.subList(0, pageSize);
        }
        if (conversations.isEmpty()) {
            return CursorPageResponse.of(
                    Collections.emptyList(), pageSize, null, null, cursor != null);
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

        List<ConversationSummaryResponse> content =
                conversations.stream()
                        .map(
                                c ->
                                        mapper.toSummaryResponse(
                                                c,
                                                participantsByConversation.getOrDefault(
                                                        c.getId(), List.of()),
                                                unreadByConversation.getOrDefault(c.getId(), 0L)))
                        .toList();
        String startCursor = encodeCursor(conversations.get(0));
        String endCursor = encodeCursor(conversations.get(conversations.size() - 1));
        return CursorPageResponse.of(content, pageSize, startCursor, endCursor, cursor != null);
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

    private void assertNotBlocked(UUID userA, UUID userB) {
        if (socialService.isBlockedBetween(userA, userB)) {
            throw new AppException(ApiErrorCode.SOCIAL_BLOCKED);
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

    private String encodeCursor(Conversation conversation) {
        // A conversation with no message yet sorts last (NULLS LAST); encoding it against the
        // earliest possible instant keeps the cursor well-formed even though, per the repository
        // query's documented trade-off, such a row is only guaranteed to appear on the first page.
        OffsetDateTime sortKey =
                conversation.getLastMessageAt() != null
                        ? conversation.getLastMessageAt()
                        : OffsetDateTime.MIN;
        String raw = sortKey + "|" + conversation.getId();
        return Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private ConversationCursor decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return new ConversationCursor(null, null);
        }
        try {
            String raw = new String(Base64.getDecoder().decode(cursor), StandardCharsets.UTF_8);
            String[] parts = raw.split("\\|", 2);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Cursor must contain lastMessageAt and id");
            }
            return new ConversationCursor(
                    OffsetDateTime.parse(parts[0]), UUID.fromString(parts[1]));
        } catch (RuntimeException e) {
            throw new AppException(ApiErrorCode.BAD_REQUEST, "Invalid cursor format");
        }
    }

    private record ConversationCursor(OffsetDateTime lastMessageAt, UUID conversationId) {
        boolean isEmpty() {
            return lastMessageAt == null || conversationId == null;
        }
    }
}
