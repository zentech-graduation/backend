package com.app.common.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Cursor-paginated response envelope following the Relay connection spec. Use for social feeds,
 * follower/following lists, comment threads, and any list whose underlying data mutates while the
 * client is paging.
 *
 * <p>Cursors are opaque base64-encoded strings produced by the repository or service layer; this
 * class performs no encoding.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CursorPageResponse<T> {

    private List<T> content;
    private PageInfo pageInfo;

    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PageInfo {
        private boolean hasNextPage;
        private boolean hasPreviousPage;

        @Schema(nullable = true)
        private String startCursor;

        @Schema(nullable = true)
        private String endCursor;
    }

    /**
     * Build a forward-paginated response from an explicit {@code hasNextPage} signal.
     *
     * <p>The caller determines whether a further page exists, typically by over-fetching one row
     * beyond the page size and observing whether the extra row was returned. Inferring the flag
     * from {@code content.size()} is not possible without that signal, because an exactly-full
     * final page is indistinguishable from a full page that has a successor.
     *
     * @param content items returned for this page
     * @param hasNextPage whether a further page exists, determined by the caller
     * @param startCursor opaque cursor for the first item; null when content is empty
     * @param endCursor opaque cursor for the last item; null when content is empty
     * @param hasPreviousPage whether a previous page exists relative to the caller's cursor
     */
    public static <T> CursorPageResponse<T> of(
            List<T> content,
            boolean hasNextPage,
            String startCursor,
            String endCursor,
            boolean hasPreviousPage) {
        return CursorPageResponse.<T>builder()
                .content(content)
                .pageInfo(
                        PageInfo.builder()
                                .hasNextPage(hasNextPage)
                                .hasPreviousPage(hasPreviousPage)
                                .startCursor(startCursor)
                                .endCursor(endCursor)
                                .build())
                .build();
    }
}
