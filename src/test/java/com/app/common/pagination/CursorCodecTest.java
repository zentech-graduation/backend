package com.app.common.pagination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.app.common.enums.ApiErrorCode;
import com.app.common.exception.AppException;

class CursorCodecTest {

    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-0000000000ab");

    @Test
    void encodeThenDecode_roundTripsSortValueAndId() {
        Cursor original = new Cursor(1_722_000_000_000_123L, ID);

        Cursor decoded = CursorCodec.decode(CursorCodec.encode(original));

        assertThat(decoded).isEqualTo(original);
    }

    @Test
    void encode_negativeSortValue_roundTrips() {
        Cursor original = new Cursor(-62_000_000_000_000L, ID);

        assertThat(CursorCodec.decode(CursorCodec.encode(original))).isEqualTo(original);
    }

    @Test
    void encode_null_returnsNull() {
        assertThat(CursorCodec.encode(null)).isNull();
    }

    @Test
    void decode_null_returnsNull() {
        assertThat(CursorCodec.decode(null)).isNull();
    }

    @Test
    void decode_blank_returnsNull() {
        assertThat(CursorCodec.decode("   ")).isNull();
    }

    @Test
    void decode_notBase64_throwsInvalidCursor() {
        assertThatThrownBy(() -> CursorCodec.decode("!!!not-base64!!!"))
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

        assertThatThrownBy(() -> CursorCodec.decode(noSeparator))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ApiErrorCode.INVALID_CURSOR);
    }

    @Test
    void decode_nonNumericSortValue_throwsInvalidCursor() {
        String badNumber =
                java.util.Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString(("abc:" + ID).getBytes());

        assertThatThrownBy(() -> CursorCodec.decode(badNumber))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ApiErrorCode.INVALID_CURSOR);
    }

    @Test
    void decode_malformedUuid_throwsInvalidCursor() {
        String badUuid =
                java.util.Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString("123:not-a-uuid".getBytes());

        assertThatThrownBy(() -> CursorCodec.decode(badUuid))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ApiErrorCode.INVALID_CURSOR);
    }
}
