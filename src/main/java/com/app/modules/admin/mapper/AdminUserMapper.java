package com.app.modules.admin.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.app.common.security.service.RefreshTokenService;
import com.app.modules.admin.dto.response.AdminUserCapabilitiesResponse;
import com.app.modules.admin.dto.response.AdminUserDetailResponse;
import com.app.modules.admin.dto.response.AdminUserListItemResponse;
import com.app.modules.admin.dto.response.AdminUserReportResponse;
import com.app.modules.admin.dto.response.AdminUserSessionResponse;
import com.app.modules.report.entity.Report;
import com.app.modules.users.entity.User;

/** Maps {@link User} and its administrative context onto the admin-only user response shapes. */
@Mapper(componentModel = "spring")
public interface AdminUserMapper {

    @Mapping(target = "isPrivate", source = "private")
    @Mapping(target = "isVerified", source = "verified")
    AdminUserListItemResponse toListItem(User user);

    List<AdminUserListItemResponse> toListItems(List<User> users);

    AdminUserSessionResponse toSessionResponse(RefreshTokenService.ActiveSession session);

    List<AdminUserSessionResponse> toSessionResponses(
            List<RefreshTokenService.ActiveSession> sessions);

    AdminUserReportResponse toReportResponse(Report report);

    List<AdminUserReportResponse> toReportResponses(List<Report> reports);

    /**
     * Assembles the detail shape from the account row and the two bounded collections gathered
     * alongside it.
     *
     * <p>Sessions and reports are passed in rather than navigated from the entity: neither is a JPA
     * association on {@code User}, and both are deliberately bounded reads.
     *
     * <p>Capabilities are passed in for a stronger reason: they are an authorization answer, and
     * deriving them here would put a second copy of the actor-and-target rules in a mapper, where
     * they would drift from the ones the write endpoints enforce.
     */
    @Mapping(target = "isPrivate", source = "user.private")
    @Mapping(target = "isVerified", source = "user.verified")
    @Mapping(target = "sessions", source = "sessions")
    @Mapping(target = "reportsAgainst", source = "reportsAgainst")
    @Mapping(target = "capabilities", source = "capabilities")
    AdminUserDetailResponse toDetail(
            User user,
            List<AdminUserSessionResponse> sessions,
            List<AdminUserReportResponse> reportsAgainst,
            AdminUserCapabilitiesResponse capabilities);
}
