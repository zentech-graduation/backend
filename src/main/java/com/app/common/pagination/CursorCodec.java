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
 * "{scope}:{sortValueMicros}:{uuid}"}. {@code scope} is a short, stable, non-secret tag for the
 * issuing endpoint (see {@link CursorScope}): a cursor decoded with a different scope than the one
 * it was encoded with is rejected, so a cursor obtained from one endpoint cannot be replayed
 * against another that happens to share the same wire form. The cursor is otherwise opaque, not
 * secret, and carries no signature: tampering yields either a different valid position or a decode
 * failure. Any malformed input raises {@link ApiErrorCode#INVALID_CURSOR}; there is no silent
 * fallback to the first page.
 */
public final class CursorCodec {

    /** Canonical 8-4-4-4-12 rendering length; anything else is not a UUID. */
    private static final int UUID_LENGTH = 36;

    private CursorCodec() {}

    /**
     * Encodes a cursor to its opaque base64url wire form, tagged with the issuing endpoint's scope.
     *
     * @param cursor decoded position; null yields null so empty-page callers pass through unchanged
     * @param scope short stable identifier for the issuing endpoint, see {@link CursorScope}
     * @return base64url cursor string, or null when {@code cursor} is null
     */
    public static String encode(Cursor cursor, String scope) {
        if (cursor == null) {
            return null;
        }
        String payload = scope + ":" + cursor.sortValueMicros() + ":" + cursor.id();
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Decodes an opaque cursor string back to its keyset position, verifying it was issued for the
     * given scope.
     *
     * @param encoded base64url cursor; null or blank means first page and yields null
     * @param scope short stable identifier the cursor is expected to carry, see {@link CursorScope}
     * @return decoded cursor, or null when {@code encoded} is null or blank
     * @throws AppException with {@link ApiErrorCode#INVALID_CURSOR} when the input is not a
     *     well-formed cursor for the given scope
     */
    public static Cursor decode(String encoded, String scope) {
        if (encoded == null || encoded.isBlank()) {
            return null;
        }
        try {
            String payload =
                    new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
            int scopeSeparator = payload.indexOf(':');
            if (scopeSeparator < 0) {
                throw new IllegalArgumentException("cursor payload has no scope separator");
            }
            if (!scope.equals(payload.substring(0, scopeSeparator))) {
                throw new IllegalArgumentException("cursor scope does not match this endpoint");
            }
            int separator = payload.indexOf(':', scopeSeparator + 1);
            if (separator < 0) {
                throw new IllegalArgumentException("cursor payload has no value separator");
            }
            long sortValueMicros = Long.parseLong(payload.substring(scopeSeparator + 1, separator));
            String identifier = payload.substring(separator + 1);
            // Length-checked before parsing because UUID.fromString accepts a short group and
            // zero-pads it, so a cursor with characters removed decodes to a different, valid
            // position instead of failing. That returns a wrong page rather than an error.
            if (identifier.length() != UUID_LENGTH) {
                throw new IllegalArgumentException("cursor identifier is not a UUID");
            }
            UUID id = UUID.fromString(identifier);
            return new Cursor(sortValueMicros, id);
        } catch (RuntimeException e) {
            throw new AppException(ApiErrorCode.INVALID_CURSOR);
        }
    }
}
