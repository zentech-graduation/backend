package com.app.modules.admin.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.app.common.ApiConstants;
import com.app.common.config.openapi.AuthenticationRequiredResponse;
import com.app.common.config.openapi.CursorErrorResponses;
import com.app.common.response.ApiResponse;
import com.app.common.response.CursorPageResponse;
import com.app.modules.admin.dto.response.UserWarningResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * OpenAPI contract for an account reading its own warnings.
 *
 * <p>Declared in the admin module although the path sits under {@code /api/v1/users}, because the
 * data is moderation data and lives in the admin module's tables. Placing the read in the users
 * module instead would make {@code users} depend on {@code admin}, which already depends on {@code
 * users}, and the cycle would be permanent.
 */
@Tag(name = "Users", description = "Profiles, settings and account-visible moderation records")
@RequestMapping(ApiConstants.Users.ROOT)
public interface UserWarningApi {

    /** Lists the calling account's own warnings, newest first. */
    @Operation(
            summary = "List my warnings",
            description =
                    "Returns one cursor page of the calling account's unrevoked warnings, newest"
                            + " first. Warnings only: strikes are moderation state and are never"
                            + " exposed here, and neither is the moderator who issued the warning. Any"
                            + " authenticated caller may read this, about itself and nothing else.")
    @CursorErrorResponses
    @AuthenticationRequiredResponse
    @GetMapping(ApiConstants.Users.ME_WARNINGS)
    ResponseEntity<ApiResponse<CursorPageResponse<UserWarningResponse>>> listOwnWarnings(
            @Parameter(description = "Opaque cursor from the previous page")
                    @RequestParam(value = "cursor", required = false)
                    String cursor,
            @Parameter(description = "Page size, 1 to 100")
                    @RequestParam(value = "limit", defaultValue = "20")
                    @Min(1)
                    @Max(100)
                    int limit);
}
