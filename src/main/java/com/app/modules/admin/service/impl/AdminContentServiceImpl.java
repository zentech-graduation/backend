package com.app.modules.admin.service.impl;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.app.common.enums.ApiErrorCode;
import com.app.common.exception.AppException;
import com.app.common.pagination.Cursor;
import com.app.common.pagination.CursorCodec;
import com.app.common.pagination.CursorScope;
import com.app.common.pagination.KeysetPage;
import com.app.common.pagination.TimeCursors;
import com.app.common.response.CursorPageResponse;
import com.app.modules.admin.dto.response.AdminCommentSummaryResponse;
import com.app.modules.admin.dto.response.AdminPostSummaryResponse;
import com.app.modules.admin.dto.response.AdminReportTargetResponse;
import com.app.modules.admin.repository.AdminContentRepository;
import com.app.modules.admin.repository.AdminReportTargetRepository;
import com.app.modules.admin.repository.AdminUserRepository;
import com.app.modules.admin.service.AdminContentService;
import com.app.modules.report.enums.ReportType;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AdminContentServiceImpl implements AdminContentService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final AdminContentRepository adminContentRepository;
    private final AdminReportTargetRepository adminReportTargetRepository;
    private final AdminUserRepository adminUserRepository;

    public AdminContentServiceImpl(
            AdminContentRepository adminContentRepository,
            AdminReportTargetRepository adminReportTargetRepository,
            AdminUserRepository adminUserRepository) {
        this.adminContentRepository = adminContentRepository;
        this.adminReportTargetRepository = adminReportTargetRepository;
        this.adminUserRepository = adminUserRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPageResponse<AdminPostSummaryResponse> listPostsForUser(
            UUID actorId, UUID userId, String cursor, int limit) {
        requireAccountExists(userId);
        int pageSize = normalizeLimit(limit);
        Cursor decoded = CursorCodec.decode(cursor, CursorScope.ADMIN_CONTENT_POSTS);
        List<AdminPostSummaryResponse> rows =
                adminContentRepository.findPostsForUser(
                        userId,
                        decoded == null ? null : TimeCursors.fromMicros(decoded.sortValueMicros()),
                        decoded == null ? null : decoded.id(),
                        pageSize + 1);
        KeysetPage.Result<AdminPostSummaryResponse> page =
                KeysetPage.of(
                        rows,
                        pageSize,
                        row -> new Cursor(TimeCursors.toMicros(row.createdAt()), row.id()),
                        CursorScope.ADMIN_CONTENT_POSTS);
        log.info("Account posts reviewed: actorId={}, targetId={}", actorId, userId);
        return CursorPageResponse.of(
                page.content(),
                page.hasNextPage(),
                page.startCursor(),
                page.endCursor(),
                cursor != null);
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPageResponse<AdminCommentSummaryResponse> listCommentsForUser(
            UUID actorId, UUID userId, String cursor, int limit) {
        requireAccountExists(userId);
        int pageSize = normalizeLimit(limit);
        Cursor decoded = CursorCodec.decode(cursor, CursorScope.ADMIN_CONTENT_COMMENTS);
        List<AdminCommentSummaryResponse> rows =
                adminContentRepository.findCommentsForUser(
                        userId,
                        decoded == null ? null : TimeCursors.fromMicros(decoded.sortValueMicros()),
                        decoded == null ? null : decoded.id(),
                        pageSize + 1);
        KeysetPage.Result<AdminCommentSummaryResponse> page =
                KeysetPage.of(
                        rows,
                        pageSize,
                        row -> new Cursor(TimeCursors.toMicros(row.createdAt()), row.id()),
                        CursorScope.ADMIN_CONTENT_COMMENTS);
        log.info("Account comments reviewed: actorId={}, targetId={}", actorId, userId);
        return CursorPageResponse.of(
                page.content(),
                page.hasNextPage(),
                page.startCursor(),
                page.endCursor(),
                cursor != null);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminReportTargetResponse getEntity(UUID actorId, ReportType entityType, UUID entityId) {
        AdminReportTargetResponse entity =
                adminReportTargetRepository
                        .find(entityType, entityId)
                        // Not-found rather than gone: unlike the report-anchored read there is no
                        // report proving the entity ever existed, so an identifier that resolves to
                        // nothing is indistinguishable from one that was never real.
                        .orElseThrow(() -> new AppException(ApiErrorCode.REPORT_TARGET_NOT_FOUND));
        log.info(
                "Entity reviewed by identifier: actorId={}, entityType={}, entityId={}",
                actorId,
                entityType,
                entityId);
        return entity;
    }

    // Resolves a soft-deleted account as well as a live one, because an investigation of an account
    // that has since been deleted is exactly when its content still has to be readable. An unknown
    // identifier is separated from an account with no content: an empty page for a account that
    // does not exist would read as "this person posted nothing".
    private void requireAccountExists(UUID userId) {
        adminUserRepository
                .findByIdIncludingDeleted(userId)
                .orElseThrow(() -> new AppException(ApiErrorCode.USER_NOT_FOUND));
    }

    private static int normalizeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(limit, MAX_PAGE_SIZE);
    }
}
