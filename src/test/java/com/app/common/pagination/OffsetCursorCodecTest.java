package com.app.common.pagination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.app.common.enums.ApiErrorCode;
import com.app.common.exception.AppException;

class OffsetCursorCodecTest {

    @Test
    void decode_nullOrBlank_yieldsFirstPage() {
        assertThat(OffsetCursorCodec.decode(null)).isZero();
        assertThat(OffsetCursorCodec.decode("")).isZero();
        assertThat(OffsetCursorCodec.decode("   ")).isZero();
    }

    @Test
    void encodeThenDecode_roundTrips() {
        for (int offset : new int[] {0, 1, 19, 20, 21, 999, OffsetCursorCodec.MAX_OFFSET}) {
            assertThat(OffsetCursorCodec.decode(OffsetCursorCodec.encode(offset)))
                    .as("offset %d", offset)
                    .isEqualTo(offset);
        }
    }

    @Test
    void wireForm_matchesPreExtractionEncoding() {
        // Vectors captured from the two per-endpoint implementations this codec replaces, so a
        // cursor issued before the extraction still decodes to the same offset afterwards.
        Map<Integer, String> vectors = new LinkedHashMap<>();
        vectors.put(0, "MA");
        vectors.put(20, "MjA");
        vectors.put(40, "NDA");
        vectors.put(60, "NjA");
        vectors.put(10_000, "MTAwMDA");

        vectors.forEach(
                (offset, wireForm) -> {
                    assertThat(OffsetCursorCodec.encode(offset))
                            .as("encode(%d)", offset)
                            .isEqualTo(wireForm);
                    assertThat(OffsetCursorCodec.decode(wireForm))
                            .as("decode(%s)", wireForm)
                            .isEqualTo(offset);
                });
    }

    @Test
    void wireForm_reproducesTheReplacedExpressionExactly() {
        for (int offset : new int[] {0, 7, 20, 12_345}) {
            String legacy =
                    Base64.getUrlEncoder()
                            .withoutPadding()
                            .encodeToString(
                                    Integer.toString(offset).getBytes(StandardCharsets.UTF_8));
            assertThat(OffsetCursorCodec.encode(offset)).as("offset %d", offset).isEqualTo(legacy);
        }
    }

    @Test
    void decode_offsetBeyondCap_throwsInvalidCursor() {
        String justPastCap = OffsetCursorCodec.encode(OffsetCursorCodec.MAX_OFFSET + 1);

        assertThatThrownBy(() -> OffsetCursorCodec.decode(justPastCap))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ApiErrorCode.INVALID_CURSOR);
    }

    @Test
    void decode_negativeOffset_throwsInvalidCursor() {
        String negative =
                Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString("-1".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> OffsetCursorCodec.decode(negative))
                .isInstanceOf(AppException.class)
                .extracting(e -> ((AppException) e).getErrorCode())
                .isEqualTo(ApiErrorCode.INVALID_CURSOR);
    }

    @Test
    void decode_malformedInput_throwsInvalidCursor() {
        // Not base64, decodes to a non-integer, and decodes to an integer beyond int range.
        for (String malformed :
                new String[] {"!!!not-base64!!!", "bm90LWFuLWludA", "MTAwMDAwMDAwMDAwMDAwMDAw"}) {
            assertThatThrownBy(() -> OffsetCursorCodec.decode(malformed))
                    .as("malformed cursor %s", malformed)
                    .isInstanceOf(AppException.class)
                    .extracting(e -> ((AppException) e).getErrorCode())
                    .isEqualTo(ApiErrorCode.INVALID_CURSOR);
        }
    }
}
