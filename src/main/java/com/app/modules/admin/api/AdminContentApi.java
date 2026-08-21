package com.app.modules.admin.api;

import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.app.common.ApiConstants;
import com.app.common.config.openapi.AuthenticationRequiredResponse;
import com.app.common.config.openapi.CursorErrorResponses;
import com.app.common.response.ApiResponse;
import com.app.common.response.CursorPageResponse;
import com.app.modules.admin.dto.response.AdminCommentSummaryResponse;
import com.app.modules.admin.dto.response.AdminPostSummaryResponse;
import com.app.modules.admin.dto.response.AdminReportTargetResponse;
import com.app.modules.report.enums.ReportType;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * OpenAPI contract for the administrative content-inspection surface.
 *
 * <p>Every read here inverts the ordinary visibility rules on purpose. A private account's posts, a
 * post by someone who has blocked the reviewer, a draft, an archived post, a comment moderation has
 * already removed: all of them are returned. Investigating an account whose content you can see
 * only if that account chose to make it public is not investigating it.
 *
 * <p>The inversion is contained rather than generalised. It lives in a dedicated repository
 * injected only inside the administrative module, and every route into it sits behind the
 * moderator-or-administrator matcher. Nothing about the ordinary product's visibility changes.
 *
 * <p>These reads are logged, not audited. They happen many times per investigation, and an audit
 * row for each would dilute a table whose purpose is recording state changes.
 */
@Tag(name = "Administration", description = "Moderation actions and immutable audit history")
@RequestMapping(ApiConstants.Admin.ROOT)
public interface AdminContentApi {

    /** Lists one account's posts for a moderation review. */
    @Operation(
            summary = "List an account's posts",
            description =
                    "Returns every post the account holds, newest first, including drafts, archived"
                            + " posts and posts moderation has already removed, and regardless of"
                            + " whether the account is private or has blocked the reviewer. Cursor"
                            + " paginated. Moderator or administrator role required.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Cursor page of the account's posts"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "Moderator or administrator role required",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "No account holds that identifier",
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
    @CursorErrorResponses
    @AuthenticationRequiredResponse
    @GetMapping(ApiConstants.Admin.CONTENT_POSTS_FOR_USER)
    ResponseEntity<ApiResponse<CursorPageResponse<AdminPostSummaryResponse>>> listPostsForUser(
            @PathVariable("userId") UUID userId,
            @Parameter(description = "Cursor from a previous page") @RequestParam(required = false)
                    String cursor,
            @Parameter(description = "Page size")
                    @RequestParam(defaultValue = "20")
                    @Min(1)
                    @Max(100)
                    int limit);

    /** Lists one account's comments for a moderation review. */
    @Operation(
            summary = "List an account's comments",
            description =
                    "Returns every comment the account holds, newest first, including ones"
                            + " moderation has already removed and ones on posts the reviewer could"
                            + " not otherwise see. Cursor paginated. Moderator or administrator role"
                            + " required.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Cursor page of the account's comments"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "Moderator or administrator role required",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "No account holds that identifier",
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
    @CursorErrorResponses
    @AuthenticationRequiredResponse
    @GetMapping(ApiConstants.Admin.CONTENT_COMMENTS_FOR_USER)
    ResponseEntity<ApiResponse<CursorPageResponse<AdminCommentSummaryResponse>>>
            listCommentsForUser(
                    @PathVariable("userId") UUID userId,
                    @Parameter(description = "Cursor from a previous page")
                            @RequestParam(required = false)
                            String cursor,
                    @Parameter(description = "Page size")
                            @RequestParam(defaultValue = "20")
                            @Min(1)
                            @Max(100)
                            int limit);

    /** Reads one entity by its own identifier for a moderation review. */
    @Operation(
            summary = "Read one entity by identifier",
            description =
                    "Returns the entity whatever its visibility or soft-delete state, in the same"
                            + " flat shape the report-anchored review uses. Takes a bare entity"
                            + " identifier, which the report-anchored read deliberately refuses to:"
                            + " the panel links to a specific post from an audit row and from an"
                            + " account's content listing, and both dead-ended without this. The"
                            + " limit here is the role rather than the report anchor. Moderator or"
                            + " administrator role required.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "The entity rendered for review"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description = "Entity type outside the enumerated set",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "Moderator or administrator role required",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "Nothing holds that identifier",
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
    @GetMapping(ApiConstants.Admin.CONTENT_ENTITY)
    ResponseEntity<ApiResponse<AdminReportTargetResponse>> getEntity(
            @Parameter(description = "Which kind of entity the identifier names", example = "post")
                    @PathVariable("entityType")
                    ReportType entityType,
            @PathVariable("entityId") UUID entityId);
}
