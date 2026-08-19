package com.app.modules.admin.api;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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
import com.app.modules.admin.dto.request.AdminWarnUserRequest;
import com.app.modules.admin.dto.response.AdminActionResponse;
import com.app.modules.admin.dto.response.AdminViolationResponse;
import com.app.modules.admin.dto.response.AdminWarnUserResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * OpenAPI contract for the warning and strike ladder.
 *
 * <p>Issuing a warning and reading a violation history are moderator work. Reversing either is an
 * administrator decision, because a reversal is the only operation here that can be used to undo
 * another moderator's judgement.
 *
 * <p>These paths sit outside {@code /api/v1/admin/users/}, which is reserved for the ADMIN-only
 * matcher, so the role boundary stays structural rather than resting on matcher ordering.
 */
@Tag(name = "Administration", description = "Moderation actions and immutable audit history")
@RequestMapping(ApiConstants.Admin.ROOT)
public interface AdminDisciplineApi {

    /** Issues one warning against an account, applying a strike when it is the third. */
    @Operation(
            summary = "Warn an account",
            description =
                    "Records one warning and, when it is the third that still counts, issues a"
                            + " strike and applies its consequence: seven days suspended for the first,"
                            + " thirty for the second, a permanent ban for the third and any after it."
                            + " A warning counts while it is unrevoked, newer than the account's most"
                            + " recent unrevoked strike, and less than ninety days old. The consequence"
                            + " is applied only when it is stronger than the account's current state,"
                            + " so a warning can never downgrade a penalty already in force; the"
                            + " resulting status is reported either way. Only an ordinary account can"
                            + " be warned. Requires MODERATOR or ADMIN.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Warning issued"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "Moderator role required, or the target is not an ordinary account",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "Account not found",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "Actor and target are the same account",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "422",
                description = "Reason key is unknown or currently disabled",
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
    @PostMapping(ApiConstants.Admin.WARN_USER)
    ResponseEntity<ApiResponse<AdminWarnUserResponse>> warnUser(
            @PathVariable("userId") UUID userId, @Valid @RequestBody AdminWarnUserRequest request);

    /** Lists an account's violation history, newest first. */
    @Operation(
            summary = "List an account's violations",
            description =
                    "Returns one cursor page of the account's unrevoked warnings, newest first. An"
                            + " administrator also sees strikes, interleaved chronologically. The"
                            + " cursor is scoped per role, so one issued to an administrator is"
                            + " rejected when replayed by a moderator. Requires MODERATOR or ADMIN.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Cursor page of violations"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "Moderator or administrator role required",
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
    @GetMapping(ApiConstants.Admin.VIOLATIONS_FOR_USER)
    ResponseEntity<ApiResponse<CursorPageResponse<AdminViolationResponse>>> listViolations(
            @PathVariable("userId") UUID userId,
            @Parameter(description = "Opaque cursor from the previous page")
                    @RequestParam(value = "cursor", required = false)
                    String cursor,
            @Parameter(description = "Page size, 1 to 100")
                    @RequestParam(value = "limit", defaultValue = "20")
                    @Min(1)
                    @Max(100)
                    int limit);

    /** Revokes one warning without touching anything it contributed to. */
    @Operation(
            summary = "Revoke a warning",
            description =
                    "Marks one warning revoked so it stops counting toward the next strike. It does"
                            + " not revoke a strike the warning contributed to and does not change the"
                            + " account's status: reversing a strike, a suspension or a ban is a"
                            + " separate, explicit decision. Requires ADMIN.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Warning revoked"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "Administrator role required",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "Warning not found",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "Warning is already revoked",
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
    @DeleteMapping(ApiConstants.Admin.REVOKE_WARNING)
    ResponseEntity<ApiResponse<AdminActionResponse>> revokeWarning(
            @PathVariable("warningId") UUID warningId,
            @Valid @RequestBody AdminActionRequest request);

    /** Revokes one strike without lifting the penalty it caused. */
    @Operation(
            summary = "Revoke a strike",
            description =
                    "Marks one strike revoked, which frees its number and stops it counting. The"
                            + " account's status is left exactly as it is: lifting the suspension or ban"
                            + " the strike caused is done through the account-status endpoints, where it"
                            + " is audited as the decision it is. Requires ADMIN.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Strike revoked"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "403",
                description = "Administrator role required",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "Strike not found",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "Strike is already revoked",
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
    @DeleteMapping(ApiConstants.Admin.REVOKE_STRIKE)
    ResponseEntity<ApiResponse<AdminActionResponse>> revokeStrike(
            @PathVariable("strikeId") UUID strikeId,
            @Valid @RequestBody AdminActionRequest request);
}
