package com.app.common.pagination;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

class KeysetPageTest {

    private static final Function<Row, Cursor> CURSOR_OF = r -> new Cursor(r.sort(), r.id());
    private static final String SCOPE = "tst";

    private record Row(long sort, UUID id) {}

    private static Row row(long sort) {
        return new Row(sort, UUID.randomUUID());
    }

    @Test
    void of_overFetchedByOne_reportsNextPageAndTrims() {
        List<Row> rows = List.of(row(3), row(2), row(1));

        KeysetPage.Result<Row> page = KeysetPage.of(rows, 2, CURSOR_OF, SCOPE);

        assertThat(page.hasNextPage()).isTrue();
        assertThat(page.content()).hasSize(2);
        assertThat(page.content()).containsExactly(rows.get(0), rows.get(1));
    }

    @Test
    void of_exactlyFullFinalPage_reportsNoNextPage() {
        List<Row> rows = List.of(row(2), row(1));

        KeysetPage.Result<Row> page = KeysetPage.of(rows, 2, CURSOR_OF, SCOPE);

        assertThat(page.hasNextPage()).isFalse();
        assertThat(page.content()).hasSize(2);
    }

    @Test
    void of_emptyResult_hasNullCursorsAndNoNextPage() {
        KeysetPage.Result<Row> page = KeysetPage.of(List.of(), 20, CURSOR_OF, SCOPE);

        assertThat(page.hasNextPage()).isFalse();
        assertThat(page.content()).isEmpty();
        assertThat(page.startCursor()).isNull();
        assertThat(page.endCursor()).isNull();
    }

    @Test
    void of_boundaryCursorsEncodeFirstAndLastRows() {
        Row first = row(9);
        Row last = row(7);
        List<Row> rows = List.of(first, row(8), last, row(6));

        KeysetPage.Result<Row> page = KeysetPage.of(rows, 3, CURSOR_OF, SCOPE);

        assertThat(CursorCodec.decode(page.startCursor(), SCOPE)).isEqualTo(CURSOR_OF.apply(first));
        assertThat(CursorCodec.decode(page.endCursor(), SCOPE)).isEqualTo(CURSOR_OF.apply(last));
    }
}
