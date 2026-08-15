package com.app.modules.comment.service.impl;

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

import com.app.modules.comment.repository.CommentLikeRepository;

@ExtendWith(MockitoExtension.class)
class CommentViewerStateServiceImplTest {

    @Mock private CommentLikeRepository commentLikeRepository;

    private CommentViewerStateServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CommentViewerStateServiceImpl(commentLikeRepository);
    }

    @Test
    void loadLikedCommentIds_nullViewer_returnsEmptyWithoutQuerying() {
        Set<UUID> result = service.loadLikedCommentIds(null, List.of(UUID.randomUUID()));

        assertThat(result).isEmpty();
        verifyNoInteractions(commentLikeRepository);
    }

    @Test
    void loadLikedCommentIds_emptyCommentIds_returnsEmptyWithoutQuerying() {
        Set<UUID> result = service.loadLikedCommentIds(UUID.randomUUID(), List.of());

        assertThat(result).isEmpty();
        verifyNoInteractions(commentLikeRepository);
    }

    @Test
    void loadLikedCommentIds_duplicateIds_collapsesIntoOneQuery() {
        UUID viewerId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        when(commentLikeRepository.findLikedCommentIds(viewerId, Set.of(commentId)))
                .thenReturn(List.of(commentId));

        Set<UUID> result =
                service.loadLikedCommentIds(viewerId, List.of(commentId, commentId, commentId));

        assertThat(result).containsExactly(commentId);
    }

    @Test
    void loadLikedCommentIds_mixedLikedAndUnliked_returnsOnlyLiked() {
        UUID viewerId = UUID.randomUUID();
        UUID liked = UUID.randomUUID();
        UUID unliked = UUID.randomUUID();
        when(commentLikeRepository.findLikedCommentIds(viewerId, Set.of(liked, unliked)))
                .thenReturn(List.of(liked));

        Set<UUID> result = service.loadLikedCommentIds(viewerId, List.of(liked, unliked));

        assertThat(result).containsExactly(liked);
    }
}
