package com.app.modules.story.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.app.modules.social.service.SocialService;
import com.app.modules.story.entity.Story;
import com.app.modules.story.repository.StoryUserRepository;
import com.app.modules.users.entity.User;

@ExtendWith(MockitoExtension.class)
class StoryVisibilityServiceImplTest {

    @Mock private StoryUserRepository storyUserRepository;
    @Mock private SocialService socialService;

    private StoryVisibilityServiceImpl service;

    private final UUID viewerId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new StoryVisibilityServiceImpl(storyUserRepository, socialService);
    }

    private Story story() {
        return Story.builder().id(UUID.randomUUID()).userId(ownerId).build();
    }

    private User owner(boolean isPrivate) {
        return User.builder().id(ownerId).isPrivate(isPrivate).build();
    }

    @Test
    void isVisibleTo_viewerIsOwner_returnsTrue() {
        assertThat(service.isVisibleTo(ownerId, story())).isTrue();
        verifyNoInteractions(socialService, storyUserRepository);
    }

    @Test
    void isVisibleTo_publicAccountNoBlock_returnsTrue() {
        when(socialService.isBlockedBetween(viewerId, ownerId)).thenReturn(false);
        when(storyUserRepository.findByIdAndDeletedAtIsNull(ownerId))
                .thenReturn(Optional.of(owner(false)));

        assertThat(service.isVisibleTo(viewerId, story())).isTrue();
    }

    @Test
    void isVisibleTo_privateAccountWithAcceptedFollow_returnsTrue() {
        when(socialService.isBlockedBetween(viewerId, ownerId)).thenReturn(false);
        when(storyUserRepository.findByIdAndDeletedAtIsNull(ownerId))
                .thenReturn(Optional.of(owner(true)));
        when(socialService.hasAcceptedFollow(viewerId, ownerId)).thenReturn(true);

        assertThat(service.isVisibleTo(viewerId, story())).isTrue();
    }

    @Test
    void isVisibleTo_privateAccountWithoutFollow_returnsFalse() {
        when(socialService.isBlockedBetween(viewerId, ownerId)).thenReturn(false);
        when(storyUserRepository.findByIdAndDeletedAtIsNull(ownerId))
                .thenReturn(Optional.of(owner(true)));
        when(socialService.hasAcceptedFollow(viewerId, ownerId)).thenReturn(false);

        assertThat(service.isVisibleTo(viewerId, story())).isFalse();
    }

    @Test
    void isVisibleTo_blockedEitherDirection_returnsFalse() {
        when(socialService.isBlockedBetween(viewerId, ownerId)).thenReturn(true);

        assertThat(service.isVisibleTo(viewerId, story())).isFalse();
        verifyNoInteractions(storyUserRepository);
    }

    @Test
    void isVisibleTo_ownerSoftDeleted_returnsFalse() {
        when(socialService.isBlockedBetween(viewerId, ownerId)).thenReturn(false);
        when(storyUserRepository.findByIdAndDeletedAtIsNull(ownerId)).thenReturn(Optional.empty());

        assertThat(service.isVisibleTo(viewerId, story())).isFalse();
    }
}
