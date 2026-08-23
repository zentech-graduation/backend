package com.app.modules.admin.service.impl;

import java.util.List;
import java.util.Map;
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
import com.app.common.security.service.RefreshTokenService;
import com.app.modules.admin.dto.request.AdminActionRequest;
import com.app.modules.admin.dto.request.AdminRoleChangeRequest;
import com.app.modules.admin.dto.response.AdminActionResponse;
import com.app.modules.admin.dto.response.AdminUserCapabilitiesResponse;
import com.app.modules.admin.dto.response.AdminUserDetailResponse;
import com.app.modules.admin.dto.response.AdminUserListItemResponse;
import com.app.modules.admin.enums.AdminActionType;
import com.app.modules.admin.mapper.AdminUserMapper;
import com.app.modules.admin.repository.AdminUserRepository;
import com.app.modules.admin.service.AdminActionRecorder;
import com.app.modules.admin.service.AdminAuthorizationService;
import com.app.modules.admin.service.AdminUserService;
import com.app.modules.admin.service.UserDisciplineService;
import com.app.modules.report.enums.ReportType;
import com.app.modules.report.repository.ReportRepository;
import com.app.modules.users.entity.User;
import com.app.modules.users.enums.UserRole;
import com.app.modules.users.enums.UserStatus;
import com.app.modules.users.repository.UserRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AdminUserServiceImpl implements AdminUserService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    /** Matches the floor the public user search enforces, for the same selectivity reason. */
    private static final int MIN_QUERY_LENGTH = 2;

    private static final int MAX_SESSIONS = 50;
    private static final int MAX_REPORTS_AGAINST = 20;
    private static final String TARGET_ENTITY_TYPE = "user";

    private final AdminUserRepository adminUserRepository;
    private final UserRepository userRepository;
    private final ReportRepository reportRepository;
    private final RefreshTokenService refreshTokenService;
    private final AdminUserMapper adminUserMapper;
    private final AdminActionRecorder adminActionRecorder;
    private final AdminAuthorizationService adminAuthorizationService;
    private final UserDisciplineService userDisciplineService;

    public AdminUserServiceImpl(
            AdminUserRepository adminUserRepository,
            UserRepository userRepository,
            ReportRepository reportRepository,
            RefreshTokenService refreshTokenService,
            AdminUserMapper adminUserMapper,
            AdminActionRecorder adminActionRecorder,
            AdminAuthorizationService adminAuthorizationService,
            UserDisciplineService userDisciplineService) {
        this.adminUserRepository = adminUserRepository;
        this.userRepository = userRepository;
        this.reportRepository = reportRepository;
        this.refreshTokenService = refreshTokenService;
        this.adminUserMapper = adminUserMapper;
        this.adminActionRecorder = adminActionRecorder;
        this.adminAuthorizationService = adminAuthorizationService;
        this.userDisciplineService = userDisciplineService;
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPageResponse<AdminUserListItemResponse> listUsers(
            UserStatus status, UserRole role, String cursor, int limit) {
        int pageSize = normalizeLimit(limit);
        Cursor decoded = CursorCodec.decode(cursor, CursorScope.ADMIN_USERS);
        List<User> rows =
                adminUserRepository.findPage(
                        status,
                        role,
                        decoded == null ? null : TimeCursors.fromMicros(decoded.sortValueMicros()),
                        decoded == null ? null : decoded.id(),
                        pageSize + 1);
        return toPage(rows, pageSize, cursor != null, CursorScope.ADMIN_USERS);
    }

    @Override
    @Transactional(readOnly = true)
    public CursorPageResponse<AdminUserListItemResponse> searchUsers(
            String query, String cursor, int limit) {
        String normalized = normalizeQuery(query);
        int pageSize = normalizeLimit(limit);
        Cursor decoded = CursorCodec.decode(cursor, CursorScope.ADMIN_USER_SEARCH);
        List<User> rows =
                adminUserRepository.search(
                        normalized,
                        parseUuidOrNull(normalized),
                        decoded == null ? null : TimeCursors.fromMicros(decoded.sortValueMicros()),
                        decoded == null ? null : decoded.id(),
                        pageSize + 1);
        return toPage(rows, pageSize, cursor != null, CursorScope.ADMIN_USER_SEARCH);
    }

    @Override
    @Transactional(readOnly = true)
    public AdminUserDetailResponse getUserDetail(UUID actorId, UUID userId) {
        User user =
                adminUserRepository
                        .findByIdIncludingDeleted(userId)
                        .orElseThrow(() -> new AppException(ApiErrorCode.USER_NOT_FOUND));
        // The actor's role is read from the source of truth here for the same reason every write
        // path in this module reads it: a role claim minted before a demotion outlives the
        // demotion, and a capability set computed from one would offer controls the write endpoint
        // then refuses.
        UserRole actorRole =
                userRepository
                        .findByIdAndDeletedAtIsNull(actorId)
                        .map(User::getRole)
                        .orElseThrow(() -> new AppException(ApiErrorCode.FORBIDDEN));
        AdminAuthorizationService.Capabilities capabilities =
                adminAuthorizationService.capabilitiesFor(
                        actorId, actorRole, user.getId(), user.getRole());
        return adminUserMapper.toDetail(
                user,
                adminUserMapper.toSessionResponses(
                        refreshTokenService.listActiveSessions(userId, MAX_SESSIONS)),
                adminUserMapper.toReportResponses(
                        reportRepository.findFirstReportsAgainstEntity(
                                ReportType.USER, userId, MAX_REPORTS_AGAINST)),
                new AdminUserCapabilitiesResponse(
                        capabilities.canChangeStatus(),
                        capabilities.canChangeRole(),
                        capabilities.assignableRoles()),
                // Served by the discipline service rather than counted here. Three active warnings
                // issue a strike, and a second copy of that predicate would produce a plausible
                // number that drifts from the one the strike decision uses.
                userDisciplineService.countActiveWarnings(userId));
    }

    @Override
    @Transactional
    public AdminActionResponse forceLogout(UUID actorId, UUID userId, AdminActionRequest request) {
        User target =
                userRepository
                        .findByIdAndDeletedAtIsNull(userId)
                        .orElseThrow(() -> new AppException(ApiErrorCode.USER_NOT_FOUND));
        int revoked = refreshTokenService.revokeAllForUser(target.getId());
        // Same transaction as the revocation. Revoking refresh tokens alone ends the ability to
        // obtain a new access token but not the access tokens already in the target's hands: the
        // blacklist is keyed on each token's own jti and no administrator holds it. Advancing the
        // epoch is what closes that window, on the REST path and on the WebSocket sweep alike,
        // because both authenticate through TokenPrincipalResolver.
        adminUserRepository.incrementTokenEpoch(target.getId());
        log.info(
                "Force logout: actorId={}, targetId={}, revokedSessions={}",
                actorId,
                userId,
                revoked);
        return adminActionRecorder.record(
                actorId,
                AdminActionType.FORCE_LOGOUT,
                userId,
                TARGET_ENTITY_TYPE,
                userId,
                request.reportId(),
                request.reason(),
                Map.of("revokedSessions", revoked));
    }

    @Override
    @Transactional
    public AdminActionResponse changeRole(
            UUID actorId, UUID userId, AdminRoleChangeRequest request) {
        // Resolved from the source of truth inside this transaction. A role claim on a token
        // outlives a demotion for the rest of the token's life, and demotion is what this
        // endpoint exists to perform.
        UserRole actorRole =
                userRepository
                        .findByIdAndDeletedAtIsNull(actorId)
                        .map(User::getRole)
                        .orElseThrow(() -> new AppException(ApiErrorCode.FORBIDDEN));
        User target =
                userRepository
                        .findByIdAndDeletedAtIsNull(userId)
                        .orElseThrow(() -> new AppException(ApiErrorCode.USER_NOT_FOUND));
        adminAuthorizationService.assertMayChangeUserRole(
                actorId, actorRole, target.getId(), target.getRole(), request.role());

        UserRole previousRole = target.getRole();
        target.setRole(request.role());
        userRepository.save(target);
        // Same transaction as the role write. A session that outlived a demotion would hold the
        // old role's capability for as long as its tokens stayed valid.
        int revoked = refreshTokenService.revokeAllForUser(target.getId());
        // For the same reason as force logout: an access token issued under the previous role
        // would otherwise stay usable until it expired. Authority itself is read from the row on
        // every request, so this is session hygiene rather than an authority fix, but it is also
        // what makes "you have been logged out" true at the moment the administrator is told it is.
        adminUserRepository.incrementTokenEpoch(target.getId());
        log.info(
                "Role changed: actorId={}, targetId={}, from={}, to={}, revokedSessions={}",
                actorId,
                userId,
                previousRole.toJson(),
                request.role().toJson(),
                revoked);

        return adminActionRecorder.record(
                actorId,
                AdminActionType.CHANGE_USER_ROLE,
                userId,
                TARGET_ENTITY_TYPE,
                userId,
                null,
                request.reason(),
                Map.of(
                        "previousRole",
                        previousRole.toJson(),
                        "newRole",
                        request.role().toJson(),
                        "revokedSessions",
                        revoked));
    }

    private CursorPageResponse<AdminUserListItemResponse> toPage(
            List<User> rows, int pageSize, boolean hasPreviousPage, String scope) {
        KeysetPage.Result<User> page =
                KeysetPage.of(
                        rows,
                        pageSize,
                        user -> new Cursor(TimeCursors.toMicros(user.getCreatedAt()), user.getId()),
                        scope);
        return CursorPageResponse.of(
                adminUserMapper.toListItems(page.content()),
                page.hasNextPage(),
                page.startCursor(),
                page.endCursor(),
                hasPreviousPage);
    }

    private static String normalizeQuery(String raw) {
        String trimmed = raw == null ? "" : raw.strip();
        if (trimmed.length() < MIN_QUERY_LENGTH) {
            throw new AppException(
                    ApiErrorCode.BAD_REQUEST,
                    "Search query must be at least " + MIN_QUERY_LENGTH + " characters");
        }
        return trimmed;
    }

    private static UUID parseUuidOrNull(String query) {
        try {
            return UUID.fromString(query);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static int normalizeLimit(int limit) {
        return limit < 1 ? DEFAULT_PAGE_SIZE : Math.min(limit, MAX_PAGE_SIZE);
    }
}
