package com.app.modules.notification.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import com.app.common.exception.AppException;
import com.app.common.response.CursorPageResponse;
import com.app.modules.notification.dto.response.NotificationResponse;
import com.app.modules.notification.entity.Notification;
import com.app.modules.notification.entity.enums.NotificationType;
import com.app.modules.notification.mapper.NotificationMapper;
import com.app.modules.notification.repository.NotificationRepository;
import com.app.modules.social.repository.BlockRepository;
import com.app.modules.users.entity.UserSettings;
import com.app.modules.users.repository.UserSettingsRepository;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

    @Mock private NotificationRepository notificationRepository;
    @Mock private UserSettingsRepository userSettingsRepository;
    @Mock private BlockRepository blockRepository;
    @Mock private NotificationMapper notificationMapper;

    private NotificationServiceImpl service;

    @BeforeEach
    void setUp() {
        service =
                new NotificationServiceImpl(
                        notificationRepository,
                        userSettingsRepository,
                        blockRepository,
                        notificationMapper);
        lenient().when(userSettingsRepository.findById(any())).thenReturn(Optional.empty());
        lenient().when(blockRepository.existsBetween(any(), any())).thenReturn(false);
    }

    @Test
    void create_selfNotification_skips() {
        UUID userId = UUID.randomUUID();

        service.create(userId, userId, NotificationType.LIKE_POST, "post", UUID.randomUUID());

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void create_notifyFollowsDisabled_skips() {
        UUID actorId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        UserSettings settings =
                UserSettings.builder().userId(recipientId).notifyFollows(false).build();
        when(userSettingsRepository.findById(recipientId)).thenReturn(Optional.of(settings));

        service.create(actorId, recipientId, NotificationType.FOLLOW, null, null);

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void create_notifyFollowRequestsDisabled_skips() {
        UUID actorId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        UserSettings settings =
                UserSettings.builder().userId(recipientId).notifyFollows(false).build();
        when(userSettingsRepository.findById(recipientId)).thenReturn(Optional.of(settings));

        service.create(actorId, recipientId, NotificationType.FOLLOW_REQUEST, null, null);

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void create_actorBlockedByRecipient_skips() {
        UUID actorId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        when(blockRepository.existsBetween(actorId, recipientId)).thenReturn(true);

        service.create(actorId, recipientId, NotificationType.LIKE_POST, "post", UUID.randomUUID());

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void create_allGuardsPass_savesNotification() {
        UUID actorId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        UUID entityId = UUID.randomUUID();

        service.create(actorId, recipientId, NotificationType.LIKE_POST, "post", entityId);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(1)).save(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.getActorId()).isEqualTo(actorId);
        assertThat(saved.getRecipientId()).isEqualTo(recipientId);
        assertThat(saved.getType()).isEqualTo(NotificationType.LIKE_POST);
    }

    @Test
    void create_noUserSettings_guardPassesThrough() {
        UUID actorId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        when(userSettingsRepository.findById(recipientId)).thenReturn(Optional.empty());

        service.create(actorId, recipientId, NotificationType.FOLLOW, null, null);

        verify(notificationRepository, times(1)).save(any());
    }

    @Test
    void markAsRead_notOwner_throws403() {
        UUID notificationId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        when(notificationRepository.findByIdAndRecipientId(notificationId, recipientId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markAsRead(notificationId, recipientId))
                .isInstanceOf(AppException.class)
                .satisfies(
                        ex ->
                                assertThat(((AppException) ex).getHttpStatus().value())
                                        .isEqualTo(403));
    }

    @Test
    void markAsRead_alreadyRead_noUpdate() {
        UUID notificationId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        Notification notification =
                Notification.builder()
                        .id(notificationId)
                        .recipientId(recipientId)
                        .type(NotificationType.LIKE_POST)
                        .isRead(true)
                        .build();
        when(notificationRepository.findByIdAndRecipientId(notificationId, recipientId))
                .thenReturn(Optional.of(notification));

        service.markAsRead(notificationId, recipientId);

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void markAsRead_unread_setsReadFields() {
        UUID notificationId = UUID.randomUUID();
        UUID recipientId = UUID.randomUUID();
        Notification notification =
                Notification.builder()
                        .id(notificationId)
                        .recipientId(recipientId)
                        .type(NotificationType.LIKE_POST)
                        .isRead(false)
                        .build();
        when(notificationRepository.findByIdAndRecipientId(notificationId, recipientId))
                .thenReturn(Optional.of(notification));

        service.markAsRead(notificationId, recipientId);

        verify(notificationRepository, times(1)).save(notification);
        assertThat(notification.isRead()).isTrue();
        assertThat(notification.getReadAt()).isNotNull();
    }

    @Test
    void markAllAsRead_called_delegatesToRepository() {
        UUID recipientId = UUID.randomUUID();

        service.markAllAsRead(recipientId);

        verify(notificationRepository).markAllAsRead(eq(recipientId), any(OffsetDateTime.class));
    }

    @Test
    void getUnreadCount_called_returnsRepositoryValue() {
        UUID recipientId = UUID.randomUUID();
        when(notificationRepository.countByRecipientIdAndIsReadFalse(recipientId)).thenReturn(7L);

        assertThat(service.getUnreadCount(recipientId)).isEqualTo(7L);
    }

    @Test
    void getUnreadCount_noUnreadNotifications_returnsZero() {
        UUID recipientId = UUID.randomUUID();
        when(notificationRepository.countByRecipientIdAndIsReadFalse(recipientId)).thenReturn(0L);

        assertThat(service.getUnreadCount(recipientId)).isEqualTo(0L);
    }

    @Test
    void listNotifications_firstPage_noCursor_returnsContent() {
        UUID recipientId = UUID.randomUUID();
        Notification row = mockNotification();
        when(notificationRepository.findByRecipientIdWithCursor(
                        eq(recipientId), eq(null), eq(null), any(PageRequest.class)))
                .thenReturn(List.of(row));
        when(notificationMapper.toResponseList(any())).thenReturn(List.of(mockResponse()));

        CursorPageResponse<NotificationResponse> result =
                service.listNotifications(recipientId, null, 20);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getPageInfo().isHasNextPage()).isFalse();
        verify(notificationRepository, never()).findById(any());
    }

    @Test
    void listNotifications_hasNextPage_whenFullPageReturned() {
        UUID recipientId = UUID.randomUUID();
        int limit = 2;
        List<Notification> rows = List.of(mockNotification(), mockNotification());
        when(notificationRepository.findByRecipientIdWithCursor(
                        eq(recipientId), eq(null), eq(null), any(PageRequest.class)))
                .thenReturn(rows);
        when(notificationMapper.toResponseList(any()))
                .thenReturn(List.of(mockResponse(), mockResponse()));

        CursorPageResponse<NotificationResponse> result =
                service.listNotifications(recipientId, null, limit);

        assertThat(result.getPageInfo().isHasNextPage()).isTrue();
    }

    @Test
    void listNotifications_fewerThanLimit_hasNoNextPage() {
        UUID recipientId = UUID.randomUUID();
        int limit = 5;
        when(notificationRepository.findByRecipientIdWithCursor(
                        eq(recipientId), eq(null), eq(null), any(PageRequest.class)))
                .thenReturn(List.of(mockNotification()));
        when(notificationMapper.toResponseList(any())).thenReturn(List.of(mockResponse()));

        CursorPageResponse<NotificationResponse> result =
                service.listNotifications(recipientId, null, limit);

        assertThat(result.getPageInfo().isHasNextPage()).isFalse();
    }

    private static Notification mockNotification() {
        return Notification.builder()
                .id(UUID.randomUUID())
                .recipientId(UUID.randomUUID())
                .type(NotificationType.FOLLOW)
                .build();
    }

    private static NotificationResponse mockResponse() {
        return new NotificationResponse(
                UUID.randomUUID(),
                null,
                "follow",
                null,
                null,
                false,
                null,
                OffsetDateTime.now(ZoneOffset.UTC));
    }
}
