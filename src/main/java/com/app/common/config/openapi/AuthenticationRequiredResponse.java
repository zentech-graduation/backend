package com.app.common.config.openapi;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.app.common.response.ApiResponse;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Composed meta-annotation declaring the 401 response every operation that requires authentication
 * returns for a missing or invalid access token.
 *
 * <p>Applied only to operations that declared no 401 at all before this annotation existed. An
 * operation that already declares 401 for a more specific reason (e.g. "refresh token invalid or
 * expired") keeps that declaration as-is: OpenAPI responses are keyed by status code, so a second
 * 401 entry for the same method does not merge with the first, it silently competes with it. See
 * {@link MalformedBodyErrorResponses} for the same rule applied to malformed-body responses.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "401",
        description = "Missing or invalid access token",
        content =
                @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = ApiResponse.class)))
public @interface AuthenticationRequiredResponse {}
