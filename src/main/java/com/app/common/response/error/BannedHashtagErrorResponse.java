package com.app.common.response.error;

import com.app.common.response.ApiResponse;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The failure envelope a {@code POST_BANNED_HASHTAG} rejection returns, with {@code data} typed.
 *
 * <p>Exists so the detail is reachable from the generated document. {@code ApiResponse.data} is
 * declared as an empty schema, which permits anything and constrains nothing, so a generated client
 * receives {@code unknown} for {@code bannedTags} and the offending tags cannot be highlighted
 * without hand-parsing the body.
 *
 * <p>Extends the envelope rather than restating its five fields, so the shape cannot drift from the
 * one the server actually sends. Never instantiated: it is a type token for the schema generator.
 */
@Schema(
        name = "BannedHashtagErrorResponse",
        description = "Failure envelope whose data names the banned hashtags in the caption")
public class BannedHashtagErrorResponse extends ApiResponse<BannedHashtagDetail> {}
