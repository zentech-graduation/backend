package com.app.common.pagination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.app.common.enums.ApiErrorCode;
import com.app.common.exception.AppException;

class CursorCodecTest {

    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-0000000000ab");
    private static final String SCOPE = "tst";
    private static final String OTHER_SCOPE = "oth";

    @Test
    void encodeThenDecode_roundTripsSortValueAndId() {
        Cursor original = new Cursor(1_722_000_000_000_123L, ID);

        Cursor decoded = CursorCodec.decode(CursorCodec.encode(original, SCOPE), SCOPE);

        assertThat(decoded).isEqualTo(original);
    }

    @Test
    void encode_negativeSortValue_roundTrips() {
        Cursor original = new Cursor(-62_000_000_000_000L, ID);

        assertThat(CursorCodec.decode(CursorCodec.encode(original, SCOPE), SCOPE))
                .isEqualTo(original);
    }

    @Test
    void encode_null_returnsNull() {
        assertThat(CursorCodec.encode(null, SCOPE)).isNull();
    }

    @Test
    void decode_null_returnsNull() {
        assertThat(CursorCodec.decode(null, SCOPE)).isNull();
    }

    @Test
    void decode_blank_returnsNull() {
        assertThat(CursorCodec.decode("   ", SCOPE)).isNull();
    }

    @Test
    void decode_notBase64_throwsInvalidCursor() {
        assertThatThrownBy(() -> CursorCodec.decode("!!!not-base64!!!", SCOPE))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ApiErrorCode.INVALID_CURSOR);
    }

    @Test
    void decode_missingSeparator_throwsInvalidCursor() {
        String noSeparator =
                java.util.Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString("12345".getBytes());

        assertThatThrownBy(() -> CursorCodec.decode(noSeparator, SCOPE))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ApiErrorCode.INVALID_CURSOR);
    }

    @Test
    void decode_nonNumericSortValue_throwsInvalidCursor() {
        String badNumber =
                java.util.Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString((SCOPE + ":abc:" + ID).getBytes());

        assertThatThrownBy(() -> CursorCodec.decode(badNumber, SCOPE))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ApiErrorCode.INVALID_CURSOR);
    }

    @Test
    void decode_malformedUuid_throwsInvalidCursor() {
        String badUuid =
                java.util.Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString((SCOPE + ":123:not-a-uuid").getBytes());

        assertThatThrownBy(() -> CursorCodec.decode(badUuid, SCOPE))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ApiErrorCode.INVALID_CURSOR);
    }

    @Test
    void decode_cursorFromDifferentScope_throwsInvalidCursor() {
        Cursor original = new Cursor(1_722_000_000_000_123L, ID);
        String encoded = CursorCodec.encode(original, SCOPE);

        assertThatThrownBy(() -> CursorCodec.decode(encoded, OTHER_SCOPE))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ApiErrorCode.INVALID_CURSOR);
    }

    @Test
    void decode_identifierShorterThanAUuid_throwsInvalidCursor() {
        // UUID.fromString zero-pads a group that is short, so an identifier with characters
        // removed parses into a different, entirely valid identifier. Dropping 2, 3, 6 or 8
        // characters from the canonical rendering all produced a decodable cursor pointing at a
        // position the caller never held, which returns a wrong page instead of an error.
        String canonical = ID.toString();
        for (int removed : new int[] {1, 2, 3, 6, 8}) {
            String truncated = canonical.substring(0, canonical.length() - removed);
            String encoded =
                    java.util.Base64.getUrlEncoder()
                            .withoutPadding()
                            .encodeToString((SCOPE + ":123:" + truncated).getBytes());

            assertThatThrownBy(() -> CursorCodec.decode(encoded, SCOPE))
                    .as("cursor with %s characters removed from its identifier", removed)
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ApiErrorCode.INVALID_CURSOR);
        }
    }

    @Test
    void decode_identifierLongerThanAUuid_throwsInvalidCursor() {
        String encoded =
                java.util.Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString((SCOPE + ":123:" + ID + "0").getBytes());

        assertThatThrownBy(() -> CursorCodec.decode(encoded, SCOPE))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ApiErrorCode.INVALID_CURSOR);
    }
}
