package com.app.common.pagination;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import com.app.common.enums.ApiErrorCode;
import com.app.common.exception.AppException;

/**
 * Encodes and decodes opaque pagination cursors.
 *
 * <p>The wire form is an unpadded base64url string over the payload {@code
 * "{sortValueMicros}:{uuid}"}. The cursor is opaque, not secret, and carries no signature:
 * tampering yields either a different valid position or a decode failure. Any malformed input
 * raises {@link ApiErrorCode#INVALID_CURSOR}; there is no silent fallback to the first page.
 */
public final class CursorCodec {

    private CursorCodec() {}

    /**
     * Encodes a cursor to its opaque base64url wire form.
     *
     * @param cursor decoded position; null yields null so empty-page callers pass through unchanged
     * @return base64url cursor string, or null when {@code cursor} is null
     */
    public static String encode(Cursor cursor) {
        if (cursor == null) {
            return null;
        }
        String payload = cursor.sortValueMicros() + ":" + cursor.id();
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Decodes an opaque cursor string back to its keyset position.
     *
     * @param encoded base64url cursor; null or blank means first page and yields null
     * @return decoded cursor, or null when {@code encoded} is null or blank
     * @throws AppException with {@link ApiErrorCode#INVALID_CURSOR} when the input is not a
     *     well-formed cursor
     */
    public static Cursor decode(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return null;
        }
        try {
            String payload =
                    new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            int separator = payload.indexOf(':');
            if (separator < 0) {
                throw new IllegalArgumentException("cursor payload has no separator");
            }
            long sortValueMicros = Long.parseLong(payload.substring(0, separator));
            UUID id = UUID.fromString(payload.substring(separator + 1));
            return new Cursor(sortValueMicros, id);
        } catch (RuntimeException e) {
            throw new AppException(ApiErrorCode.INVALID_CURSOR);
        }
    }
}
