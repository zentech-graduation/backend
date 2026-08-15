package com.app.modules.comment.service.impl;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.app.modules.comment.repository.CommentLikeRepository;
import com.app.modules.comment.service.CommentViewerStateService;

@Service
public class CommentViewerStateServiceImpl implements CommentViewerStateService {

    private final CommentLikeRepository commentLikeRepository;

    public CommentViewerStateServiceImpl(CommentLikeRepository commentLikeRepository) {
        this.commentLikeRepository = commentLikeRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> loadLikedCommentIds(UUID viewerId, Collection<UUID> commentIds) {
        if (viewerId == null || commentIds == null || commentIds.isEmpty()) {
            return Set.of();
        }
        Set<UUID> distinct = new LinkedHashSet<>(commentIds);
        return new HashSet<>(commentLikeRepository.findLikedCommentIds(viewerId, distinct));
    }
}
