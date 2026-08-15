package com.app.common.pagination;

import java.util.List;
import java.util.function.Function;

/**
 * Turns an over-fetched keyset result into a trimmed page with an exact {@code hasNextPage} flag
 * and boundary cursors.
 *
 * <p>Callers fetch {@code limit + 1} rows. The extra row, when present, proves a further page
 * exists without a second query and without the off-by-one error of inferring {@code hasNextPage}
 * from a page that happens to be exactly full.
 */
public final class KeysetPage {

    private KeysetPage() {}

    /**
     * Trims an over-fetched row list to {@code limit} and computes page metadata.
     *
     * @param rows result of a {@code limit + 1} keyset query, in final sort order
     * @param limit requested page size
     * @param cursorOf maps a boundary row to its cursor position
     * @param scope short stable identifier for the issuing endpoint, see {@link CursorScope}
     * @param <E> row type
     * @return trimmed content with exact {@code hasNextPage} and encoded boundary cursors
     */
    public static <E> Result<E> of(
            List<E> rows, int limit, Function<E, Cursor> cursorOf, String scope) {
        boolean hasNextPage = rows.size() > limit;
        List<E> content = hasNextPage ? rows.subList(0, limit) : rows;
        String startCursor =
                content.isEmpty()
                        ? null
                        : CursorCodec.encode(cursorOf.apply(content.get(0)), scope);
        String endCursor =
                content.isEmpty()
                        ? null
                        : CursorCodec.encode(
                                cursorOf.apply(content.get(content.size() - 1)), scope);
        return new Result<>(content, hasNextPage, startCursor, endCursor);
    }

    /**
     * Trimmed keyset page.
     *
     * @param content rows for this page, at most {@code limit}
     * @param hasNextPage whether a further page exists, proven by the over-fetched row
     * @param startCursor cursor of the first row, null when empty
     * @param endCursor cursor of the last row, null when empty
     * @param <E> row type
     */
    public record Result<E>(
            List<E> content, boolean hasNextPage, String startCursor, String endCursor) {}
}
