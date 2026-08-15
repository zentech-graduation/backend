package com.app.common.pagination;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import com.app.common.enums.ApiErrorCode;
import com.app.common.exception.AppException;

/**
 * Encodes and decodes opaque offset cursors for relevance-ranked search endpoints.
 *
 * <p>Distinct from {@link CursorCodec}, which encodes a {@code {sortValueMicros}:{uuid}} keyset
 * position. Relevance-ranked results have no stable sort key to resume from, so those endpoints
 * page by offset instead. The wire form is an unpadded base64url string over the decimal offset,
 * matching byte-for-byte the per-endpoint implementations this replaces, so cursors issued before
 * the extraction still decode afterwards.
 *
 * <p>Any malformed input raises {@link ApiErrorCode#INVALID_CURSOR}; there is no silent fallback to
 * the first page.
 */
public final class OffsetCursorCodec {

    /**
     * Deepest offset a cursor may address.
     *
     * <p>Bounds two distinct costs: an Elasticsearch {@code from} beyond the index result window
     * errors and counts as a circuit-breaker failure, and a deep PostgreSQL {@code OFFSET} scans
     * and discards every skipped row. A forged cursor must not be able to drive either.
     */
    public static final int MAX_OFFSET = 10_000;

    private OffsetCursorCodec() {}

    /**
     * Encodes an offset to its opaque base64url wire form.
     *
     * @param offset zero-based row offset
     * @return unpadded base64url cursor string
     */
    public static String encode(int offset) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(Integer.toString(offset).getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Decodes an opaque cursor string back to its row offset.
     *
     * @param encoded base64url cursor; null or blank means first page and yields 0
     * @return the decoded offset, always within {@code [0, MAX_OFFSET]}
     * @throws AppException with {@link ApiErrorCode#INVALID_CURSOR} when the input is not a
     *     well-formed cursor or addresses an offset outside the permitted range
     */
    public static int decode(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return 0;
        }
        try {
            int offset =
                    Integer.parseInt(
                            new String(
                                    Base64.getUrlDecoder().decode(encoded),
                                    StandardCharsets.UTF_8));
            if (offset < 0 || offset > MAX_OFFSET) {
                throw new NumberFormatException("offset out of range");
            }
            return offset;
        } catch (RuntimeException e) {
            throw new AppException(ApiErrorCode.INVALID_CURSOR);
        }
    }
}
