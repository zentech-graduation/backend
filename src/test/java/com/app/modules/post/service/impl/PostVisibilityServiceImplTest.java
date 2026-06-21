package com.app.modules.post.service.impl;

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

import com.app.modules.post.entity.Post;
import com.app.modules.post.repository.PostUserRepository;
import com.app.modules.social.service.SocialService;
import com.app.modules.users.entity.User;

@ExtendWith(MockitoExtension.class)
class PostVisibilityServiceImplTest {

    @Mock private PostUserRepository postUserRepository;
    @Mock private SocialService socialService;

    private PostVisibilityServiceImpl service;

    private final UUID viewerId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new PostVisibilityServiceImpl(postUserRepository, socialService);
    }

    private Post post() {
        return Post.builder().id(UUID.randomUUID()).userId(ownerId).build();
    }

    private User owner(boolean isPrivate) {
        return User.builder().id(ownerId).isPrivate(isPrivate).build();
    }

    @Test
    void isVisibleTo_viewerIsOwner_returnsTrue() {
        assertThat(service.isVisibleTo(ownerId, post())).isTrue();
        verifyNoInteractions(socialService, postUserRepository);
    }

    @Test
    void isVisibleTo_publicAccountNoBlock_returnsTrue() {
        when(socialService.isBlockedBetween(viewerId, ownerId)).thenReturn(false);
        when(postUserRepository.findByIdAndDeletedAtIsNull(ownerId))
                .thenReturn(Optional.of(owner(false)));

        assertThat(service.isVisibleTo(viewerId, post())).isTrue();
    }

    @Test
    void isVisibleTo_privateAccountWithAcceptedFollow_returnsTrue() {
        when(socialService.isBlockedBetween(viewerId, ownerId)).thenReturn(false);
        when(postUserRepository.findByIdAndDeletedAtIsNull(ownerId))
                .thenReturn(Optional.of(owner(true)));
        when(socialService.hasAcceptedFollow(viewerId, ownerId)).thenReturn(true);

        assertThat(service.isVisibleTo(viewerId, post())).isTrue();
    }

    @Test
    void isVisibleTo_privateAccountWithoutFollow_returnsFalse() {
        when(socialService.isBlockedBetween(viewerId, ownerId)).thenReturn(false);
        when(postUserRepository.findByIdAndDeletedAtIsNull(ownerId))
                .thenReturn(Optional.of(owner(true)));
        when(socialService.hasAcceptedFollow(viewerId, ownerId)).thenReturn(false);

        assertThat(service.isVisibleTo(viewerId, post())).isFalse();
    }

    @Test
    void isVisibleTo_blockedEitherDirection_returnsFalse() {
        when(socialService.isBlockedBetween(viewerId, ownerId)).thenReturn(true);

        assertThat(service.isVisibleTo(viewerId, post())).isFalse();
        verifyNoInteractions(postUserRepository);
    }

    @Test
    void isVisibleTo_ownerSoftDeleted_returnsFalse() {
        when(socialService.isBlockedBetween(viewerId, ownerId)).thenReturn(false);
        when(postUserRepository.findByIdAndDeletedAtIsNull(ownerId)).thenReturn(Optional.empty());

        assertThat(service.isVisibleTo(viewerId, post())).isFalse();
    }
}
