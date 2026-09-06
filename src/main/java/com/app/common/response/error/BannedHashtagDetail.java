package com.app.common.response.error;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The {@code data} a {@code POST_BANNED_HASHTAG} rejection carries.
 *
 * @param bannedTags normalized names of the hashtags in the caption an administrator has banned
 */
@Schema(description = "Detail carried by a POST_BANNED_HASHTAG rejection")
public record BannedHashtagDetail(
        @Schema(
                        description =
                                "Normalized names of the banned hashtags the caption contains, so"
                                        + " a client can point at them in the caption rather than"
                                        + " saying something in it is not allowed",
                        example = "[\"spamtag\"]")
                List<String> bannedTags) {}
