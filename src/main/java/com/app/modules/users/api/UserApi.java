package com.app.modules.users.api;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

import com.app.common.ApiConstants;
import com.app.common.response.ApiResponse;
import com.app.common.security.user.UserPrincipal;
import com.app.modules.users.dto.request.UpdateProfileRequest;
import com.app.modules.users.dto.request.UpdateSettingsRequest;
import com.app.modules.users.dto.response.PublicUserProfileResponse;
import com.app.modules.users.dto.response.UserProfileResponse;
import com.app.modules.users.dto.response.UserSettingsResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/** OpenAPI contract for the users module. */
@Tag(name = "Users", description = "User profile and settings management")
@RequestMapping(ApiConstants.Users.ROOT)
public interface UserApi {

    @Operation(
            summary = "Get my profile",
            description = "Returns the full profile of the authenticated user.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Profile returned",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = UserProfileResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "Missing or invalid access token",
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
    @GetMapping(ApiConstants.Users.ME)
    ResponseEntity<ApiResponse<UserProfileResponse>> getMyProfile();

    @Operation(
            summary = "Update my profile",
            description =
                    "Applies partial updates to the authenticated user's profile. Null fields are"
                            + " ignored; an empty string clears the field.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Profile updated",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = UserProfileResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "Missing or invalid access token",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "409",
                description = "Username is already taken",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "422",
                description = "Validation failure",
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
    @PatchMapping(ApiConstants.Users.ME)
    ResponseEntity<ApiResponse<UserProfileResponse>> updateMyProfile(
            @Valid @RequestBody UpdateProfileRequest request);

    @Operation(
            summary = "Get a user's public profile",
            description =
                    "Returns the public profile of the specified user. Private accounts return"
                            + " 401. Counter fields are omitted for unauthenticated callers.",
            security = {})
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Profile returned",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema =
                                        @Schema(implementation = PublicUserProfileResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "Target account is private",
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
    @GetMapping(ApiConstants.Users.BY_ID)
    ResponseEntity<ApiResponse<PublicUserProfileResponse>> getUserProfile(
            @PathVariable UUID userId, @AuthenticationPrincipal UserPrincipal principal);

    @Operation(
            summary = "Get a user's public profile by username",
            description =
                    "Returns the public profile of the user holding the given username. Matching is"
                            + " case-sensitive, because username uniqueness is enforced on the raw"
                            + " column. A non-existent username, a soft-deleted account, and an"
                            + " account blocked with respect to the caller all return 404"
                            + " identically. Counter fields are omitted for unauthenticated"
                            + " callers.",
            security = {})
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Profile returned",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema =
                                        @Schema(implementation = PublicUserProfileResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "404",
                description = "No live user holds that username, or the account is block-hidden",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "400",
                description =
                        "Username is outside 3-30 characters or contains illegal characters. Path"
                                + " and query constraint violations return 400, unlike request-body"
                                + " violations which return 422.",
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
    @GetMapping(ApiConstants.Users.BY_USERNAME)
    ResponseEntity<ApiResponse<PublicUserProfileResponse>> getUserProfileByUsername(
            @PathVariable
                    @Size(min = 3, max = 30)
                    @Pattern(
                            regexp = "^[a-zA-Z0-9_.]+$",
                            message =
                                    "Username may only contain letters, digits, underscores and"
                                            + " dots")
                    String username,
            @AuthenticationPrincipal UserPrincipal principal);

    @Operation(
            summary = "Get my settings",
            description =
                    "Returns the notification and privacy settings for the authenticated user.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Settings returned",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = UserSettingsResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "Missing or invalid access token",
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
    @GetMapping(ApiConstants.Users.ME_SETTINGS)
    ResponseEntity<ApiResponse<UserSettingsResponse>> getMySettings();

    @Operation(
            summary = "Update my settings",
            description =
                    "Applies partial updates to the authenticated user's notification and privacy"
                            + " settings. Null fields are ignored.",
            security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "200",
                description = "Settings updated",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = UserSettingsResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "401",
                description = "Missing or invalid access token",
                content =
                        @Content(
                                mediaType = "application/json",
                                schema = @Schema(implementation = ApiResponse.class))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(
                responseCode = "422",
                description = "Validation failure",
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
    @PatchMapping(ApiConstants.Users.ME_SETTINGS)
    ResponseEntity<ApiResponse<UserSettingsResponse>> updateMySettings(
            @Valid @RequestBody UpdateSettingsRequest request);
}
