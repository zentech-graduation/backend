package com.app.modules.admin.api;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.app.common.ApiConstants;
import com.app.common.config.openapi.AuthenticationRequiredResponse;
import com.app.common.config.openapi.CursorErrorResponses;
import com.app.common.config.openapi.MalformedBodyErrorResponses;
import com.app.common.response.ApiResponse;
import com.app.common.response.CursorPageResponse;
import com.app.modules.admin.dto.request.AdminActionRequest;
import com.app.modules.admin.dto.request.AdminRoleChangeRequest;
import com.app.modules.admin.dto.response.AdminActionResponse;
import com.app.modules.admin.dto.response.AdminUserDetailResponse;
import com.app.modules.admin.dto.response.AdminUserListItemResponse;
import com.app.modules.users.enums.UserRole;
import com.app.modules.users.enums.UserStatus;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * OpenAPI contract for the administrative account surface.
 *
 * <p>Every operation here requires the administrator role. The reads deliberately span every
 * account status and include soft-deleted accounts, unlike every public user surface.
 */
@Tag(name = "Administration", description = "Moderation actions and immutable audit history")
@RequestMapping(ApiConstants.Admin.ROOT)
public interface AdminUserApi {

    /** Lists accounts newest first, optionally narrowed by status and role. */
    @Operation(
            summary = "List accounts",
            description =
                    "Returns one cursor page of accounts ordered by creation time descending."
                            + " Spans every account status and includes soft-deleted accounts, so"
                            + " an account removed from every public surface is still visible here.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Cursor page of accounts"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "Administrator role required",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "429",
                description = "Rate limit exceeded",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class)))
    })
    @AuthenticationRequiredResponse
    @CursorErrorResponses
    @GetMapping(ApiConstants.Admin.USERS)
    ResponseEntity<ApiResponse<CursorPageResponse<AdminUserListItemResponse>>> listUsers(
            @Parameter(description = "Restrict to accounts holding this status")
                    @RequestParam(required = false)
                    UserStatus status,
            @Parameter(description = "Restrict to accounts holding this role")
                    @RequestParam(required = false)
                    UserRole role,
            @Parameter(description = "Opaque cursor from a previous page")
                    @RequestParam(required = false)
                    String cursor,
            @Parameter(description = "Page size")
                    @RequestParam(defaultValue = "20")
                    @Min(1)
                    @Max(100)
                    int limit);

    /** Searches accounts by id, username, or email. */
    @Operation(
            summary = "Search accounts",
            description =
                    "Matches the query against the account id when it parses as a UUID, and always"
                            + " against username and email as a case-insensitive substring."
                            + " Requires at least two characters. Spans every account status and"
                            + " includes soft-deleted accounts, so this search finds accounts the"
                            + " public user search deliberately hides.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Cursor page of matching accounts"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "Query shorter than two characters, or malformed cursor",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "Administrator role required",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "429",
                description = "Rate limit exceeded",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class)))
    })
    @AuthenticationRequiredResponse
    @GetMapping(ApiConstants.Admin.USER_SEARCH)
    ResponseEntity<ApiResponse<CursorPageResponse<AdminUserListItemResponse>>> searchUsers(
            @Parameter(
                            description = "Account id, or a username or email substring",
                            required = true)
                    @RequestParam("q")
                    String query,
            @Parameter(description = "Opaque cursor from a previous page")
                    @RequestParam(required = false)
                    String cursor,
            @Parameter(description = "Page size")
                    @RequestParam(defaultValue = "20")
                    @Min(1)
                    @Max(100)
                    int limit);

    /** Returns the full administrative view of one account. */
    @Operation(
            summary = "Get account detail",
            description =
                    "Returns one account with its registration and last-login origin, its live"
                            + " sessions, and the most recent reports filed against it. Resolves a"
                            + " soft-deleted account as well as a live one. Also carries what the"
                            + " requesting administrator may do to the account, evaluated against"
                            + " the same component the write endpoints enforce, so a control can be"
                            + " rendered from the payload rather than from a client-side copy of"
                            + " the rules.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Account detail"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "Administrator role required",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "User not found",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "429",
                description = "Rate limit exceeded",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class)))
    })
    @AuthenticationRequiredResponse
    @GetMapping(ApiConstants.Admin.USER_BY_ID)
    ResponseEntity<ApiResponse<AdminUserDetailResponse>> getUserDetail(
            @PathVariable("userId") UUID userId);

    /** Revokes every live session of an account and returns the persisted audit event. */
    @Operation(
            summary = "Force logout",
            description =
                    "Revokes every refresh token the account holds, ending its ability to obtain a"
                            + " new access token immediately. An access token already in the"
                            + " account's hands keeps working until it expires: the blacklist is"
                            + " keyed on the token's own jti and no administrator holds it, so"
                            + " access capability ends within ACCESS_TOKEN_TTL rather than at once."
                            + " The audit metadata records how many sessions were revoked.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Sessions revoked"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "Administrator role required",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "User not found",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "429",
                description = "Rate limit exceeded",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class)))
    })
    @AuthenticationRequiredResponse
    @MalformedBodyErrorResponses
    @PostMapping(ApiConstants.Admin.USER_FORCE_LOGOUT)
    ResponseEntity<ApiResponse<AdminActionResponse>> forceLogout(
            @PathVariable("userId") UUID userId, @Valid @RequestBody AdminActionRequest request);

    /** Revokes one named session and returns the persisted audit event. */
    @Operation(
            summary = "Revoke one session",
            description =
                    "Ends a single session rather than every session the account holds. The"
                            + " session must belong to the account named in the path; naming"
                            + " another account's session answers 404, because the session listing"
                            + " hands identifiers out and this endpoint must not become a way to"
                            + " end any session in the system. Revoking a session that is already"
                            + " revoked or expired succeeds rather than failing, so a reviewer"
                            + " clicking twice sees no error; the audit row's metadata carries"
                            + " alreadyRevoked so the two are still told apart. This does not"
                            + " advance the account's token epoch, unlike force logout: the epoch"
                            + " is per account, so advancing it would sign the account out"
                            + " everywhere while reporting that one session was ended.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Session revoked, or already was"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "Administrator role required",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "Account not found, or no such session belongs to it",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "429",
                description = "Rate limit exceeded",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class)))
    })
    @MalformedBodyErrorResponses
    @AuthenticationRequiredResponse
    @DeleteMapping(ApiConstants.Admin.USER_SESSION_BY_ID)
    ResponseEntity<ApiResponse<AdminActionResponse>> revokeSession(
            @PathVariable("userId") UUID userId,
            @PathVariable("sessionId") UUID sessionId,
            @Valid @RequestBody AdminActionRequest request);

    /** Changes an account's role and returns the persisted audit event. */
    @Operation(
            summary = "Change account role",
            description =
                    "Permitted transitions are user to moderator, moderator to user, and moderator"
                            + " to admin. An administrator is never a valid target, so demoting one"
                            + " is deliberately a database-level operation and is refused with 403"
                            + " ADMIN_TARGET_PROTECTED, the same answer a status change gives for"
                            + " the same target. A skip-level promotion from user to admin and a"
                            + " request naming the role the account already holds are conflicts"
                            + " with the current state and are refused with 409. The change revokes"
                            + " every session the account holds in the same transaction as the role"
                            + " write.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Role changed"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description =
                        "Administrator role required, or the target is an administrator and"
                                + " therefore protected",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "User not found",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description =
                        "The actor is the target, the promotion skips a level, or the account"
                                + " already holds the requested role",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "429",
                description = "Rate limit exceeded",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class)))
    })
    @AuthenticationRequiredResponse
    @MalformedBodyErrorResponses
    @PatchMapping(ApiConstants.Admin.USER_ROLE)
    ResponseEntity<ApiResponse<AdminActionResponse>> changeRole(
            @PathVariable("userId") UUID userId,
            @Valid @RequestBody AdminRoleChangeRequest request);
}
