package com.app.common.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class CursorPageResponseTest {

    @Test
    void of_exactlyFullFinalPage_reportsNoNextPage() {
        List<String> fullPage = List.of("a", "b", "c");

        CursorPageResponse<String> response =
                CursorPageResponse.of(fullPage, false, "start", "end", false);

        assertThat(response.getPageInfo().isHasNextPage()).isFalse();
        assertThat(response.getContent()).isEqualTo(fullPage);
    }

    @Test
    void of_moreRowsAvailable_reportsNextPage() {
        CursorPageResponse<String> response =
                CursorPageResponse.of(List.of("a", "b", "c"), true, "start", "end", false);

        assertThat(response.getPageInfo().isHasNextPage()).isTrue();
    }

    @Test
    void of_carriesPreviousPageAndCursorsVerbatim() {
        CursorPageResponse<String> response =
                CursorPageResponse.of(List.of("only"), false, "s", "e", true);

        assertThat(response.getPageInfo().isHasPreviousPage()).isTrue();
        assertThat(response.getPageInfo().getStartCursor()).isEqualTo("s");
        assertThat(response.getPageInfo().getEndCursor()).isEqualTo("e");
    }

    @Test
    void of_neverClaimsDegradation() {
        // Every existing caller uses of(), so a complete result must keep reporting itself
        // complete.
        assertThat(CursorPageResponse.of(List.of("a"), false, null, null, false).isDegraded())
                .isFalse();
    }

    @Test
    void of_emptyGenuineNoMatch_isNotDegraded() {
        // The case the flag exists to separate: an empty page that is a real answer, not an outage.
        assertThat(CursorPageResponse.of(List.of(), false, null, null, false).isDegraded())
                .isFalse();
    }

    @Test
    void degraded_isEmptyCursorlessAndFlagged() {
        CursorPageResponse<String> response = CursorPageResponse.degraded();

        assertThat(response.isDegraded()).isTrue();
        assertThat(response.getContent()).isEmpty();
        assertThat(response.getPageInfo().isHasNextPage()).isFalse();
        assertThat(response.getPageInfo().isHasPreviousPage()).isFalse();
        assertThat(response.getPageInfo().getStartCursor()).isNull();
        assertThat(response.getPageInfo().getEndCursor()).isNull();
    }
}
