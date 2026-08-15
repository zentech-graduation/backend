package com.app.modules.post.service.impl;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.app.modules.post.repository.PostLikeRepository;
import com.app.modules.post.repository.PostSaveRepository;
import com.app.modules.post.service.PostViewerState;
import com.app.modules.post.service.PostViewerStateService;
import com.app.modules.report.enums.ReportType;
import com.app.modules.report.service.ReportedTargetService;

@Service
public class PostViewerStateServiceImpl implements PostViewerStateService {

    private final PostLikeRepository postLikeRepository;
    private final PostSaveRepository postSaveRepository;
    private final ReportedTargetService reportedTargetService;

    public PostViewerStateServiceImpl(
            PostLikeRepository postLikeRepository,
            PostSaveRepository postSaveRepository,
            ReportedTargetService reportedTargetService) {
        this.postLikeRepository = postLikeRepository;
        this.postSaveRepository = postSaveRepository;
        this.reportedTargetService = reportedTargetService;
    }

    @Override
    @Transactional(readOnly = true)
    public PostViewerState load(UUID viewerId, Collection<UUID> postIds) {
        if (viewerId == null || postIds == null || postIds.isEmpty()) {
            return PostViewerState.NONE;
        }
        Set<UUID> distinct = new LinkedHashSet<>(postIds);
        Set<UUID> liked = new HashSet<>(postLikeRepository.findLikedPostIds(viewerId, distinct));
        Set<UUID> saved = new HashSet<>(postSaveRepository.findSavedPostIds(viewerId, distinct));
        Set<UUID> reported =
                reportedTargetService.loadReportedEntityIds(viewerId, ReportType.POST, distinct);
        return new PostViewerState(liked, saved, reported);
    }
}
