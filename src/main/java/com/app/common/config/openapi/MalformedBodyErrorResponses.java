package com.app.common.config.openapi;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.app.common.response.ApiResponse;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Composed meta-annotation declaring the 400 response every body-accepting endpoint returns for a
 * request body that could not be read: an unrecognised property, invalid JSON syntax, a wrong root
 * type, or an invalid enum value.
 *
 * <p>A wholly absent body is a further cause only on handlers whose {@code @RequestBody} is
 * required. Where a handler declares {@code required = false}, an absent body is valid input and
 * never produces this 400; the remaining causes above still do, which is why those handlers keep
 * this annotation.
 *
 * <p>Applied only to the endpoints that declared no 400 at all before this annotation existed. An
 * endpoint that already declares 400 for validation failure keeps that declaration as-is: OpenAPI
 * responses are keyed by status code, so a second 400 entry for the same method does not merge with
 * the first, it silently competes with it. See {@link CursorErrorResponses} for the same rule
 * applied to cursor endpoints.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "Malformed or unparseable request body",
        content =
                @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = ApiResponse.class)))
@io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "415",
        description = "Request body was sent with an unsupported Content-Type",
        content =
                @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = ApiResponse.class)))
public @interface MalformedBodyErrorResponses {}
