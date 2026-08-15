package com.app.modules.post.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.app.modules.post.repository.PostLikeRepository;
import com.app.modules.post.repository.PostSaveRepository;
import com.app.modules.post.service.PostViewerState;
import com.app.modules.report.service.ReportedTargetService;

@ExtendWith(MockitoExtension.class)
class PostViewerStateServiceImplTest {

    @Mock private PostLikeRepository postLikeRepository;
    @Mock private PostSaveRepository postSaveRepository;
    @Mock private ReportedTargetService reportedTargetService;

    private PostViewerStateServiceImpl service;

    @BeforeEach
    void setUp() {
        service =
                new PostViewerStateServiceImpl(
                        postLikeRepository, postSaveRepository, reportedTargetService);
    }

    @Test
    void load_nullViewer_returnsNoneWithoutQuerying() {
        PostViewerState result = service.load(null, List.of(UUID.randomUUID()));

        assertThat(result).isSameAs(PostViewerState.NONE);
        verifyNoInteractions(postLikeRepository, postSaveRepository);
    }

    @Test
    void load_emptyPostIds_returnsNoneWithoutQuerying() {
        PostViewerState result = service.load(UUID.randomUUID(), List.of());

        assertThat(result).isSameAs(PostViewerState.NONE);
        verifyNoInteractions(postLikeRepository, postSaveRepository);
    }

    @Test
    void load_duplicatePostIds_collapsesIntoOneQueryEach() {
        UUID viewerId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        when(postLikeRepository.findLikedPostIds(viewerId, Set.of(postId)))
                .thenReturn(List.of(postId));
        when(postSaveRepository.findSavedPostIds(viewerId, Set.of(postId))).thenReturn(List.of());

        PostViewerState result = service.load(viewerId, List.of(postId, postId, postId));

        assertThat(result.isLiked(postId)).isTrue();
        assertThat(result.isSaved(postId)).isFalse();
    }

    @Test
    void load_mixedLikedAndSaved_resolvesEachIndependently() {
        UUID viewerId = UUID.randomUUID();
        UUID likedOnly = UUID.randomUUID();
        UUID savedOnly = UUID.randomUUID();
        UUID neither = UUID.randomUUID();
        Set<UUID> ids = Set.of(likedOnly, savedOnly, neither);
        when(postLikeRepository.findLikedPostIds(viewerId, ids)).thenReturn(List.of(likedOnly));
        when(postSaveRepository.findSavedPostIds(viewerId, ids)).thenReturn(List.of(savedOnly));

        PostViewerState result = service.load(viewerId, List.of(likedOnly, savedOnly, neither));

        assertThat(result.isLiked(likedOnly)).isTrue();
        assertThat(result.isSaved(likedOnly)).isFalse();
        assertThat(result.isLiked(savedOnly)).isFalse();
        assertThat(result.isSaved(savedOnly)).isTrue();
        assertThat(result.isLiked(neither)).isFalse();
        assertThat(result.isSaved(neither)).isFalse();
    }
}
