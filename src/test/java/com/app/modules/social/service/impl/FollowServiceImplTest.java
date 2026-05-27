package com.app.modules.social.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.app.common.enums.ApiErrorCode;
import com.app.common.exception.AppException;
import com.app.modules.social.dto.response.FollowResponse;
import com.app.modules.social.entity.Follow;
import com.app.modules.social.entity.FollowId;
import com.app.modules.social.enums.FollowStatus;
import com.app.modules.social.mapper.FollowMapper;
import com.app.modules.social.repository.BlockRepository;
import com.app.modules.social.repository.FollowRepository;
import com.app.modules.social.service.SocialEventService;
import com.app.modules.users.entity.User;
import com.app.modules.users.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class FollowServiceImplTest {

    @Mock private FollowRepository followRepository;
    @Mock private BlockRepository blockRepository;
    @Mock private UserRepository userRepository;
    @Mock private SocialEventService socialEventService;
    @Mock private FollowMapper followMapper;

    private FollowServiceImpl service;

    @BeforeEach
    void setUp() {
        service =
                new FollowServiceImpl(
                        followRepository,
                        blockRepository,
                        userRepository,
                        socialEventService,
                        followMapper);
    }

    @Test
    void follow_publicTarget_createsAcceptedFollowAndPublishesEvent() {
        UUID followerId = UUID.randomUUID();
        UUID followingId = UUID.randomUUID();
        User target = user(followingId, false);
        Follow inserted = follow(followerId, followingId, FollowStatus.ACCEPTED);
        FollowResponse response =
                new FollowResponse(
                        followerId, followingId, FollowStatus.ACCEPTED, inserted.getCreatedAt());
        when(userRepository.findByIdAndDeletedAtIsNull(followingId))
                .thenReturn(Optional.of(target));
        when(blockRepository.existsBetween(followerId, followingId)).thenReturn(false);
        when(followRepository.existsById(new FollowId(followerId, followingId))).thenReturn(false);
        when(followRepository.insert(followerId, followingId, FollowStatus.ACCEPTED))
                .thenReturn(inserted);
        when(followMapper.toResponse(inserted)).thenReturn(response);

        FollowResponse result = service.follow(followerId, followingId);

        assertThat(result).isSameAs(response);
        verify(followRepository).insert(followerId, followingId, FollowStatus.ACCEPTED);
        verify(socialEventService).publishFollowCreated(inserted);
        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void follow_privateTarget_createsPendingFollowAndPublishesEvent() {
        UUID followerId = UUID.randomUUID();
        UUID followingId = UUID.randomUUID();
        User target = user(followingId, true);
        Follow inserted = follow(followerId, followingId, FollowStatus.PENDING);
        when(userRepository.findByIdAndDeletedAtIsNull(followingId))
                .thenReturn(Optional.of(target));
        when(blockRepository.existsBetween(followerId, followingId)).thenReturn(false);
        when(followRepository.existsById(new FollowId(followerId, followingId))).thenReturn(false);
        when(followRepository.insert(followerId, followingId, FollowStatus.PENDING))
                .thenReturn(inserted);

        service.follow(followerId, followingId);

        verify(followRepository).insert(followerId, followingId, FollowStatus.PENDING);
        verify(socialEventService).publishFollowCreated(inserted);
    }

    @Test
    void follow_selfFollow_throwsBadRequestBeforeDbAccess() {
        UUID userId = UUID.randomUUID();

        assertThatThrownBy(() -> service.follow(userId, userId))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ApiErrorCode.SOCIAL_SELF_FOLLOW_NOT_ALLOWED);

        verify(followRepository, never())
                .insert(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());
        verify(socialEventService, never())
                .publishFollowCreated(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void follow_duplicateFollow_throwsConflictAndSkipsEvent() {
        UUID followerId = UUID.randomUUID();
        UUID followingId = UUID.randomUUID();
        when(userRepository.findByIdAndDeletedAtIsNull(followingId))
                .thenReturn(Optional.of(user(followingId, false)));
        when(blockRepository.existsBetween(followerId, followingId)).thenReturn(false);
        when(followRepository.existsById(new FollowId(followerId, followingId))).thenReturn(true);

        assertThatThrownBy(() -> service.follow(followerId, followingId))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ApiErrorCode.SOCIAL_FOLLOW_ALREADY_EXISTS);

        verify(followRepository, never())
                .insert(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());
        verify(socialEventService, never())
                .publishFollowCreated(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void follow_blockRelationship_throwsForbiddenAndSkipsInsert() {
        UUID followerId = UUID.randomUUID();
        UUID followingId = UUID.randomUUID();
        when(userRepository.findByIdAndDeletedAtIsNull(followingId))
                .thenReturn(Optional.of(user(followingId, false)));
        when(blockRepository.existsBetween(followerId, followingId)).thenReturn(true);

        assertThatThrownBy(() -> service.follow(followerId, followingId))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ApiErrorCode.SOCIAL_FOLLOW_BLOCKED);

        verify(followRepository, never())
                .insert(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());
        verify(socialEventService, never())
                .publishFollowCreated(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void follow_targetNotFound_throwsNotFoundAndSkipsInsert() {
        UUID followerId = UUID.randomUUID();
        UUID followingId = UUID.randomUUID();
        when(userRepository.findByIdAndDeletedAtIsNull(followingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.follow(followerId, followingId))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ApiErrorCode.NOT_FOUND);

        verify(followRepository, never())
                .insert(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any());
        verify(socialEventService, never())
                .publishFollowCreated(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void follow_duplicateInsertRace_mapsUniqueViolationToConflict() {
        UUID followerId = UUID.randomUUID();
        UUID followingId = UUID.randomUUID();
        when(userRepository.findByIdAndDeletedAtIsNull(followingId))
                .thenReturn(Optional.of(user(followingId, false)));
        when(blockRepository.existsBetween(followerId, followingId)).thenReturn(false);
        when(followRepository.existsById(new FollowId(followerId, followingId))).thenReturn(false);
        when(followRepository.insert(followerId, followingId, FollowStatus.ACCEPTED))
                .thenThrow(new DataIntegrityViolationException("duplicate", sqlException("23505")));

        assertThatThrownBy(() -> service.follow(followerId, followingId))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ApiErrorCode.SOCIAL_FOLLOW_ALREADY_EXISTS);

        verify(socialEventService, never())
                .publishFollowCreated(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void follow_unknownIntegrityViolation_isNotMaskedAsClientError() {
        UUID followerId = UUID.randomUUID();
        UUID followingId = UUID.randomUUID();
        DataIntegrityViolationException exception =
                new DataIntegrityViolationException("unknown", sqlException("99999"));
        when(userRepository.findByIdAndDeletedAtIsNull(followingId))
                .thenReturn(Optional.of(user(followingId, false)));
        when(blockRepository.existsBetween(followerId, followingId)).thenReturn(false);
        when(followRepository.existsById(new FollowId(followerId, followingId))).thenReturn(false);
        when(followRepository.insert(followerId, followingId, FollowStatus.ACCEPTED))
                .thenThrow(exception);

        assertThatThrownBy(() -> service.follow(followerId, followingId)).isSameAs(exception);

        verify(socialEventService, never())
                .publishFollowCreated(org.mockito.ArgumentMatchers.any());
    }

    private static Follow follow(UUID followerId, UUID followingId, FollowStatus status) {
        return Follow.builder().id(new FollowId(followerId, followingId)).status(status).build();
    }

    private static User user(UUID id, boolean isPrivate) {
        return User.builder()
                .id(id)
                .username("user_" + id.toString().substring(0, 8))
                .email(id + "@example.com")
                .isPrivate(isPrivate)
                .build();
    }

    private static SQLException sqlException(String sqlState) {
        return new SQLException("constraint violation", sqlState);
    }
}
