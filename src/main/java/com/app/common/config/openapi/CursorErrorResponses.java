package com.app.common.config.openapi;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import com.app.common.response.ApiResponse;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Composed meta-annotation declaring the 400 response every cursor-paginated endpoint returns for a
 * malformed pagination cursor.
 *
 * <p>Applying this instead of hand-writing the same {@code @ApiResponse} entry on each of the 21
 * cursor endpoints keeps the declaration in one place: extending or correcting its wording cannot
 * drift out of step across endpoints the way the per-operation {@code @Schema(implementation =
 * ...)} overrides that caused this document's original defect did.
 *
 * <p>Combines with any {@code @ApiResponses} block already present on the same method:
 * {@code @ApiResponse} is {@code @Repeatable}, and springdoc merges repeatable annotations found
 * directly on a method with those found through meta-annotation composition.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "Malformed cursor",
        content =
                @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = ApiResponse.class)))
public @interface CursorErrorResponses {}
