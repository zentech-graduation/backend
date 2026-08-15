package com.app.modules.message.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

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
import com.app.modules.message.dto.response.ConversationResponse;
import com.app.modules.message.dto.response.ConversationSummaryResponse;
import com.app.modules.message.dto.response.ParticipantResponse;
import com.app.modules.message.entity.Conversation;
import com.app.modules.message.entity.ConversationParticipant;
import com.app.modules.message.entity.ConversationParticipantId;
import com.app.modules.message.mapper.MessageMapper;
import com.app.modules.message.repository.ConversationParticipantRepository;
import com.app.modules.message.repository.ConversationRepository;
import com.app.modules.message.repository.MessageRepository;
import com.app.modules.message.repository.MessageUserRepository;
import com.app.modules.message.repository.MessageUserSettingsRepository;
import com.app.modules.social.service.SocialService;
import com.app.modules.users.entity.User;
import com.app.modules.users.entity.UserSettings;

@ExtendWith(MockitoExtension.class)
class ConversationServiceImplTest {

    @Mock private ConversationRepository conversationRepository;
    @Mock private ConversationParticipantRepository participantRepository;
    @Mock private MessageRepository messageRepository;
    @Mock private MessageUserRepository userRepository;
    @Mock private MessageUserSettingsRepository userSettingsRepository;
    @Mock private SocialService socialService;
    @Mock private MessageMapper mapper;

    private ConversationServiceImpl service;

    @BeforeEach
    void setUp() {
        service =
                new ConversationServiceImpl(
                        conversationRepository,
                        participantRepository,
                        messageRepository,
                        userRepository,
                        userSettingsRepository,
                        socialService,
                        new MessageProperties(24, true, 256),
                        mapper);

        lenient().when(socialService.isBlockedBetween(any(), any())).thenReturn(false);
        lenient().when(userSettingsRepository.findById(any())).thenReturn(Optional.empty());
        lenient()
                .when(mapper.toParticipantResponse(any(), any()))
                .thenAnswer(
                        inv -> {
                            ConversationParticipant p = inv.getArgument(0);
                            User u = inv.getArgument(1);
                            return new ParticipantResponse(
                                    p.getId().getUserId(),
                                    u == null ? null : u.getUsername(),
                                    u == null ? null : u.getDisplayName(),
                                    u == null ? null : u.getAvatarUrl(),
                                    p.isAdmin(),
                                    p.getJoinedAt(),
                                    p.getLeftAt());
                        });
        lenient()
                .when(mapper.toConversationResponse(any(), any()))
                .thenAnswer(
                        inv -> {
                            Conversation c = inv.getArgument(0);
                            List<ParticipantResponse> participants = inv.getArgument(1);
                            return new ConversationResponse(
                                    c.getId(),
                                    c.isGroup(),
                                    c.getGroupName(),
                                    c.getGroupAvatarUrl(),
                                    c.getCreatedBy(),
                                    participants,
                                    c.getLastMessageAt(),
                                    c.getCreatedAt());
                        });
        lenient()
                .when(
                        mapper.toSummaryResponse(
                                any(), any(), org.mockito.ArgumentMatchers.anyLong()))
                .thenAnswer(
                        inv -> {
                            Conversation c = inv.getArgument(0);
                            List<ParticipantResponse> participants = inv.getArgument(1);
                            long unread = inv.getArgument(2);
                            return new ConversationSummaryResponse(
                                    c.getId(),
                                    c.isGroup(),
                                    c.getGroupName(),
                                    c.getGroupAvatarUrl(),
                                    participants,
                                    unread,
                                    c.getLastMessageAt());
                        });
    }

    @Test
    void createDirectConversation_selfConversation_throwsInvalidParticipants() {
        UUID actorId = UUID.randomUUID();

        assertThatThrownBy(
                        () ->
                                service.createDirectConversation(
                                        actorId, new CreateDirectConversationRequest(actorId)))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ApiErrorCode.CONVERSATION_INVALID_PARTICIPANTS);
        verify(userRepository, never()).findByIdAndDeletedAtIsNull(any());
    }

    @Test
    void createDirectConversation_targetNotFound_throwsUserNotFound() {
        UUID actorId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        when(userRepository.findByIdAndDeletedAtIsNull(targetId)).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                service.createDirectConversation(
                                        actorId, new CreateDirectConversationRequest(targetId)))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ApiErrorCode.USER_NOT_FOUND);
    }

    @Test
    void createDirectConversation_blockedPair_throwsNotFound() {
        // Stealth block model: a blocked target must be indistinguishable from a nonexistent one.
        UUID actorId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        when(userRepository.findByIdAndDeletedAtIsNull(targetId))
                .thenReturn(Optional.of(user(targetId)));
        when(socialService.isBlockedBetween(actorId, targetId)).thenReturn(true);

        assertThatThrownBy(
                        () ->
                                service.createDirectConversation(
                                        actorId, new CreateDirectConversationRequest(targetId)))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ApiErrorCode.NOT_FOUND);
        verify(conversationRepository, never()).findDirectConversationBetween(any(), any());
    }

    @Test
    void
            createDirectConversation_requestsDisabledAndNotFollowedBack_throwsMessageRequestNotAllowed() {
        UUID actorId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        when(userRepository.findByIdAndDeletedAtIsNull(targetId))
                .thenReturn(Optional.of(user(targetId)));
        when(userSettingsRepository.findById(targetId))
                .thenReturn(Optional.of(settingsWithRequestsDisabled(targetId)));
        when(socialService.hasAcceptedFollow(targetId, actorId)).thenReturn(false);

        assertThatThrownBy(
                        () ->
                                service.createDirectConversation(
                                        actorId, new CreateDirectConversationRequest(targetId)))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ApiErrorCode.MESSAGE_REQUEST_NOT_ALLOWED);
        verify(conversationRepository, never()).findDirectConversationBetween(any(), any());
    }

    @Test
    void createDirectConversation_requestsDisabledButFollowedBack_createsConversation() {
        UUID actorId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        when(userRepository.findByIdAndDeletedAtIsNull(targetId))
                .thenReturn(Optional.of(user(targetId)));
        when(userSettingsRepository.findById(targetId))
                .thenReturn(Optional.of(settingsWithRequestsDisabled(targetId)));
        when(socialService.hasAcceptedFollow(targetId, actorId)).thenReturn(true);
        when(conversationRepository.findDirectConversationBetween(actorId, targetId))
                .thenReturn(Optional.empty());
        when(conversationRepository.saveAndFlush(any(Conversation.class)))
                .thenAnswer(
                        inv -> {
                            Conversation c = inv.getArgument(0);
                            c.setId(conversationId);
                            return c;
                        });
        when(participantRepository.findByIdConversationIdOrderByJoinedAtAsc(conversationId))
                .thenReturn(List.of());

        ConversationResponse result =
                service.createDirectConversation(
                        actorId, new CreateDirectConversationRequest(targetId));

        assertThat(result).isNotNull();
        verify(participantRepository).save(argThat(p -> p.getId().getUserId().equals(actorId)));
        verify(participantRepository).save(argThat(p -> p.getId().getUserId().equals(targetId)));
    }

    @Test
    void createDirectConversation_newConversation_locksPairAndPersistsPairKey() {
        UUID actorId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        String pairKey = actorId + ":" + targetId;
        when(userRepository.findByIdAndDeletedAtIsNull(targetId))
                .thenReturn(Optional.of(user(targetId)));
        when(conversationRepository.lockDirectConversationPair(actorId, targetId))
                .thenReturn(pairKey);
        when(conversationRepository.findDirectConversationBetween(actorId, targetId))
                .thenReturn(Optional.empty());
        when(conversationRepository.saveAndFlush(any(Conversation.class)))
                .thenAnswer(
                        inv -> {
                            Conversation c = inv.getArgument(0);
                            c.setId(conversationId);
                            return c;
                        });
        when(participantRepository.findByIdConversationIdOrderByJoinedAtAsc(conversationId))
                .thenReturn(List.of());

        service.createDirectConversation(actorId, new CreateDirectConversationRequest(targetId));

        // The lock must be acquired before the check-then-create so a concurrent call for the same
        // pair serializes behind it instead of racing into a duplicate conversation.
        verify(conversationRepository).lockDirectConversationPair(actorId, targetId);
        verify(conversationRepository)
                .saveAndFlush(argThat(c -> pairKey.equals(c.getDirectPairKey())));
    }

    @Test
    void createDirectConversation_existingConversationWithLeftActor_reactivatesAndReuses() {
        UUID actorId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        Conversation existing = Conversation.builder().id(conversationId).isGroup(false).build();
        ConversationParticipant actorParticipant =
                participant(
                        conversationId,
                        actorId,
                        false,
                        OffsetDateTime.now(ZoneOffset.UTC).minusDays(1));

        when(userRepository.findByIdAndDeletedAtIsNull(targetId))
                .thenReturn(Optional.of(user(targetId)));
        when(conversationRepository.findDirectConversationBetween(actorId, targetId))
                .thenReturn(Optional.of(existing));
        when(participantRepository.findByIdConversationIdAndIdUserId(conversationId, actorId))
                .thenReturn(Optional.of(actorParticipant));
        when(participantRepository.findByIdConversationIdOrderByJoinedAtAsc(conversationId))
                .thenReturn(List.of(actorParticipant));
        when(userRepository.findAllByIdInAndDeletedAtIsNull(Set.of(actorId)))
                .thenReturn(List.of(user(actorId)));

        ConversationResponse result =
                service.createDirectConversation(
                        actorId, new CreateDirectConversationRequest(targetId));

        assertThat(result).isNotNull();
        assertThat(actorParticipant.getLeftAt()).isNull();
        verify(participantRepository).save(actorParticipant);
        verify(conversationRepository, never()).saveAndFlush(any());
    }

    @Test
    void createGroupConversation_groupChatDisabled_throwsGroupChatDisabled() {
        ConversationServiceImpl disabledService =
                new ConversationServiceImpl(
                        conversationRepository,
                        participantRepository,
                        messageRepository,
                        userRepository,
                        userSettingsRepository,
                        socialService,
                        new MessageProperties(24, false, 256),
                        mapper);
        UUID actorId = UUID.randomUUID();

        assertThatThrownBy(
                        () ->
                                disabledService.createGroupConversation(
                                        actorId,
                                        new CreateGroupRequest(
                                                "Group", null, List.of(UUID.randomUUID()))))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ApiErrorCode.GROUP_CHAT_DISABLED);
    }

    @Test
    void createGroupConversation_success_creatorIsAdminAndMembersAreNot() {
        UUID actorId = UUID.randomUUID();
        UUID member1Id = UUID.randomUUID();
        UUID member2Id = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        User member1 = user(member1Id);
        User member2 = user(member2Id);

        when(userRepository.findAllByIdInAndDeletedAtIsNull(List.of(member1Id, member2Id)))
                .thenReturn(List.of(member1, member2));
        when(conversationRepository.saveAndFlush(any(Conversation.class)))
                .thenAnswer(
                        inv -> {
                            Conversation c = inv.getArgument(0);
                            c.setId(conversationId);
                            return c;
                        });
        ConversationParticipant creatorParticipant =
                participant(conversationId, actorId, true, null);
        ConversationParticipant member1Participant =
                participant(conversationId, member1Id, false, null);
        ConversationParticipant member2Participant =
                participant(conversationId, member2Id, false, null);
        when(participantRepository.findByIdConversationIdOrderByJoinedAtAsc(conversationId))
                .thenReturn(List.of(creatorParticipant, member1Participant, member2Participant));
        when(userRepository.findAllByIdInAndDeletedAtIsNull(Set.of(actorId, member1Id, member2Id)))
                .thenReturn(List.of(user(actorId), member1, member2));

        ConversationResponse result =
                service.createGroupConversation(
                        actorId,
                        new CreateGroupRequest(
                                "Trip Planning", null, List.of(member1Id, member2Id)));

        assertThat(result).isNotNull();
        verify(participantRepository)
                .save(argThat(p -> p.getId().getUserId().equals(actorId) && p.isAdmin()));
        verify(participantRepository)
                .save(argThat(p -> p.getId().getUserId().equals(member1Id) && !p.isAdmin()));
        verify(participantRepository)
                .save(argThat(p -> p.getId().getUserId().equals(member2Id) && !p.isAdmin()));
    }

    @Test
    void addParticipants_callerNotAdmin_throwsGroupAdminRequired() {
        UUID conversationId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        Conversation group = Conversation.builder().id(conversationId).isGroup(true).build();
        ConversationParticipant nonAdmin = participant(conversationId, actorId, false, null);
        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(group));
        when(participantRepository.findByIdConversationIdAndIdUserId(conversationId, actorId))
                .thenReturn(Optional.of(nonAdmin));

        assertThatThrownBy(
                        () ->
                                service.addParticipants(
                                        actorId,
                                        conversationId,
                                        new AddParticipantsRequest(List.of(UUID.randomUUID()))))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ApiErrorCode.GROUP_ADMIN_REQUIRED);
    }

    @Test
    void addParticipants_onDirectConversation_throwsConversationNotGroup() {
        UUID conversationId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        Conversation direct = Conversation.builder().id(conversationId).isGroup(false).build();
        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(direct));

        assertThatThrownBy(
                        () ->
                                service.addParticipants(
                                        actorId,
                                        conversationId,
                                        new AddParticipantsRequest(List.of(UUID.randomUUID()))))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ApiErrorCode.CONVERSATION_NOT_GROUP);
    }

    @Test
    void removeParticipant_callerIsActiveAdmin_setsLeftAtOnTarget() {
        UUID conversationId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        Conversation group = Conversation.builder().id(conversationId).isGroup(true).build();
        ConversationParticipant admin = participant(conversationId, actorId, true, null);
        ConversationParticipant target = participant(conversationId, targetId, false, null);
        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(group));
        when(participantRepository.findByIdConversationIdAndIdUserId(conversationId, actorId))
                .thenReturn(Optional.of(admin));
        when(participantRepository.findByIdConversationIdAndIdUserId(conversationId, targetId))
                .thenReturn(Optional.of(target));

        service.removeParticipant(actorId, conversationId, targetId);

        assertThat(target.getLeftAt()).isNotNull();
        verify(participantRepository).save(target);
    }

    @Test
    void removeParticipant_targetNotActiveParticipant_throwsParticipantNotFound() {
        UUID conversationId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        Conversation group = Conversation.builder().id(conversationId).isGroup(true).build();
        ConversationParticipant admin = participant(conversationId, actorId, true, null);
        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(group));
        when(participantRepository.findByIdConversationIdAndIdUserId(conversationId, actorId))
                .thenReturn(Optional.of(admin));
        when(participantRepository.findByIdConversationIdAndIdUserId(conversationId, targetId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.removeParticipant(actorId, conversationId, targetId))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ApiErrorCode.PARTICIPANT_NOT_FOUND);
    }

    @Test
    void removeParticipant_soleAdminRemovesSelf_promotesReplacementAdmin() {
        UUID conversationId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        Conversation group = Conversation.builder().id(conversationId).isGroup(true).build();
        ConversationParticipant admin = participant(conversationId, adminId, true, null);
        ConversationParticipant other = participant(conversationId, otherId, false, null);
        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(group));
        when(participantRepository.findByIdConversationIdAndIdUserId(conversationId, adminId))
                .thenReturn(Optional.of(admin));
        when(participantRepository.findByIdConversationIdOrderByJoinedAtAsc(conversationId))
                .thenReturn(List.of(admin, other));

        service.removeParticipant(adminId, conversationId, adminId);

        assertThat(admin.getLeftAt()).isNotNull();
        assertThat(other.isAdmin()).isTrue();
        verify(participantRepository).save(admin);
        verify(participantRepository).save(other);
    }

    @Test
    void removeParticipant_nonAdminTargetRemoved_doesNotPromoteAnyone() {
        UUID conversationId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        Conversation group = Conversation.builder().id(conversationId).isGroup(true).build();
        ConversationParticipant admin = participant(conversationId, actorId, true, null);
        ConversationParticipant target = participant(conversationId, targetId, false, null);
        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(group));
        when(participantRepository.findByIdConversationIdAndIdUserId(conversationId, actorId))
                .thenReturn(Optional.of(admin));
        when(participantRepository.findByIdConversationIdAndIdUserId(conversationId, targetId))
                .thenReturn(Optional.of(target));

        service.removeParticipant(actorId, conversationId, targetId);

        assertThat(target.getLeftAt()).isNotNull();
        verify(participantRepository, never()).findByIdConversationIdOrderByJoinedAtAsc(any());
    }

    @Test
    void leaveConversation_lastActiveAdminLeaves_promotesOldestRemainingMember() {
        UUID conversationId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID otherId = UUID.randomUUID();
        Conversation group = Conversation.builder().id(conversationId).isGroup(true).build();
        ConversationParticipant leavingAdmin = participant(conversationId, actorId, true, null);
        ConversationParticipant remainingMember = participant(conversationId, otherId, false, null);
        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(group));
        when(participantRepository.findByIdConversationIdAndIdUserId(conversationId, actorId))
                .thenReturn(Optional.of(leavingAdmin));
        when(participantRepository.findByIdConversationIdOrderByJoinedAtAsc(conversationId))
                .thenReturn(List.of(leavingAdmin, remainingMember));

        service.leaveConversation(actorId, conversationId);

        assertThat(leavingAdmin.getLeftAt()).isNotNull();
        assertThat(remainingMember.isAdmin()).isTrue();
        verify(participantRepository).save(leavingAdmin);
        verify(participantRepository).save(remainingMember);
    }

    @Test
    void leaveConversation_alreadyLeft_isNoop() {
        UUID conversationId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        Conversation group = Conversation.builder().id(conversationId).isGroup(true).build();
        ConversationParticipant alreadyLeft =
                participant(
                        conversationId,
                        actorId,
                        true,
                        OffsetDateTime.now(ZoneOffset.UTC).minusDays(1));
        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(group));
        when(participantRepository.findByIdConversationIdAndIdUserId(conversationId, actorId))
                .thenReturn(Optional.of(alreadyLeft));

        service.leaveConversation(actorId, conversationId);

        verify(participantRepository, never()).save(any());
    }

    @Test
    void getConversation_callerNotActiveParticipant_throwsConversationForbidden() {
        UUID conversationId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        Conversation conversation =
                Conversation.builder().id(conversationId).isGroup(false).build();
        when(conversationRepository.findById(conversationId)).thenReturn(Optional.of(conversation));
        when(participantRepository.existsByIdConversationIdAndIdUserIdAndLeftAtIsNull(
                        conversationId, actorId))
                .thenReturn(false);

        assertThatThrownBy(() -> service.getConversation(actorId, conversationId))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ApiErrorCode.CONVERSATION_FORBIDDEN);
    }

    @Test
    void listMyConversations_firstPage_returnsSummariesWithCursors() {
        UUID actorId = UUID.randomUUID();
        UUID conv1Id = UUID.randomUUID();
        UUID conv2Id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        Conversation conv1 =
                Conversation.builder().id(conv1Id).isGroup(false).lastMessageAt(now).build();
        Conversation conv2 =
                Conversation.builder()
                        .id(conv2Id)
                        .isGroup(false)
                        .lastMessageAt(now.minusMinutes(5))
                        .build();
        when(conversationRepository.findFirstMyConversations(eq(actorId), any(Pageable.class)))
                .thenReturn(List.of(conv1, conv2));
        when(participantRepository.findByIdConversationIdInAndLeftAtIsNull(
                        List.of(conv1Id, conv2Id)))
                .thenReturn(List.of());
        when(messageRepository.countUnreadPerConversation(
                        eq(actorId), eq(List.of(conv1Id, conv2Id))))
                .thenReturn(List.of());

        CursorPageResponse<ConversationSummaryResponse> result =
                service.listMyConversations(actorId, null, 20);

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getPageInfo().getStartCursor()).isNotBlank();
        assertThat(result.getPageInfo().getEndCursor()).isNotBlank();
        assertThat(result.getPageInfo().isHasPreviousPage()).isFalse();
    }

    @Test
    void listMyConversations_noActiveConversations_returnsEmptyPageWithNullCursors() {
        UUID actorId = UUID.randomUUID();
        when(conversationRepository.findFirstMyConversations(eq(actorId), any(Pageable.class)))
                .thenReturn(List.of());

        CursorPageResponse<ConversationSummaryResponse> result =
                service.listMyConversations(actorId, null, 20);

        assertThat(result.getContent()).isEmpty();
        assertThat(result.getPageInfo().getStartCursor()).isNull();
        assertThat(result.getPageInfo().getEndCursor()).isNull();
        assertThat(result.getPageInfo().isHasPreviousPage()).isFalse();
    }

    @Test
    void listMyConversations_withCursor_decodesAndQueriesContinuation() {
        UUID actorId = UUID.randomUUID();
        UUID conv1Id = UUID.randomUUID();
        // Truncated to microsecond resolution: the cursor round-trips through epoch-micros, and a
        // nanosecond remainder would make the decoded value compare unequal to this one.
        OffsetDateTime cursorTime =
                TimeCursors.fromMicros(
                        TimeCursors.toMicros(OffsetDateTime.now(ZoneOffset.UTC).minusHours(1)));
        UUID cursorConvId = UUID.randomUUID();
        String cursor =
                CursorCodec.encode(
                        new Cursor(TimeCursors.toMicros(cursorTime), cursorConvId),
                        CursorScope.CONVERSATIONS);
        Conversation conv1 =
                Conversation.builder()
                        .id(conv1Id)
                        .isGroup(false)
                        .lastMessageAt(cursorTime.minusMinutes(10))
                        .build();
        when(conversationRepository.findMyConversationsBefore(
                        eq(actorId), eq(cursorTime), eq(cursorConvId), any(Pageable.class)))
                .thenReturn(List.of(conv1));
        when(participantRepository.findByIdConversationIdInAndLeftAtIsNull(List.of(conv1Id)))
                .thenReturn(List.of());
        when(messageRepository.countUnreadPerConversation(eq(actorId), eq(List.of(conv1Id))))
                .thenReturn(List.of());

        CursorPageResponse<ConversationSummaryResponse> result =
                service.listMyConversations(actorId, cursor, 20);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getPageInfo().isHasPreviousPage()).isTrue();
    }

    @Test
    void listMyConversations_cursorFromNullLastMessageAtRow_decodesToNullCursorTime() {
        UUID actorId = UUID.randomUUID();
        UUID conv1Id = UUID.randomUUID();
        Conversation conv1 =
                Conversation.builder().id(conv1Id).isGroup(false).lastMessageAt(null).build();
        when(conversationRepository.findFirstMyConversations(eq(actorId), any(Pageable.class)))
                .thenReturn(List.of(conv1));
        when(participantRepository.findByIdConversationIdInAndLeftAtIsNull(List.of(conv1Id)))
                .thenReturn(List.of());
        when(messageRepository.countUnreadPerConversation(eq(actorId), eq(List.of(conv1Id))))
                .thenReturn(List.of());

        CursorPageResponse<ConversationSummaryResponse> firstPage =
                service.listMyConversations(actorId, null, 20);
        String endCursor = firstPage.getPageInfo().getEndCursor();
        assertThat(endCursor).isNotBlank();

        UUID conv2Id = UUID.randomUUID();
        Conversation conv2 =
                Conversation.builder().id(conv2Id).isGroup(false).lastMessageAt(null).build();
        // Previously this cursor decoded to OffsetDateTime.MIN (a sentinel for "no message yet"),
        // which is outside PostgreSQL's timestamptz range and caused a bind-time 500; it must now
        // decode back to a genuine null so the repository receives real SQL NULL, not a sentinel.
        when(conversationRepository.findMyConversationsBefore(
                        eq(actorId), isNull(), eq(conv1Id), any(Pageable.class)))
                .thenReturn(List.of(conv2));
        when(participantRepository.findByIdConversationIdInAndLeftAtIsNull(List.of(conv2Id)))
                .thenReturn(List.of());
        when(messageRepository.countUnreadPerConversation(eq(actorId), eq(List.of(conv2Id))))
                .thenReturn(List.of());

        CursorPageResponse<ConversationSummaryResponse> secondPage =
                service.listMyConversations(actorId, endCursor, 20);

        assertThat(secondPage.getContent()).hasSize(1);
        verify(conversationRepository)
                .findMyConversationsBefore(eq(actorId), isNull(), eq(conv1Id), any(Pageable.class));
    }

    private static User user(UUID id) {
        return User.builder().id(id).username("user-" + id).displayName("Display " + id).build();
    }

    private static UserSettings settingsWithRequestsDisabled(UUID userId) {
        return UserSettings.builder().userId(userId).allowMessageRequests(false).build();
    }

    private static ConversationParticipant participant(
            UUID conversationId, UUID userId, boolean admin, OffsetDateTime leftAt) {
        return ConversationParticipant.builder()
                .id(new ConversationParticipantId(conversationId, userId))
                .isAdmin(admin)
                .leftAt(leftAt)
                .build();
    }
}
