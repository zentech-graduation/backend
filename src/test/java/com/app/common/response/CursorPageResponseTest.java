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
}
