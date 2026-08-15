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

    /**
     * True when this page was produced by a degraded backend rather than by a complete query.
     *
     * <p>Only the post search fallback sets it. Without it an empty page returned because
     * Elasticsearch was unreachable is byte-identical to a genuine no-match apart from the response
     * timestamp, so a client cannot tell "nothing matched" from "the search tier is down" and
     * cannot honestly word an empty state.
     *
     * <p>Defaults to false, and every existing factory path leaves it false, so no response that
     * was complete before now claims to be degraded.
     */
    @Schema(
            description =
                    "True when the results are incomplete because a backing service was"
                            + " unavailable. False on a complete result, including a genuine"
                            + " no-match.")
    private boolean degraded;

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

    /**
     * Build an empty page that declares itself incomplete because a backing service was
     * unavailable.
     *
     * <p>Separate from {@link #of} so that marking a page degraded is always deliberate: no
     * existing caller can acquire the flag by accident, and a reader of a fallback method can see
     * the claim being made at the call site.
     *
     * @return an empty page with no cursors and {@code degraded} set
     */
    public static <T> CursorPageResponse<T> degraded() {
        return CursorPageResponse.<T>builder()
                .content(List.of())
                .pageInfo(
                        PageInfo.builder()
                                .hasNextPage(false)
                                .hasPreviousPage(false)
                                .startCursor(null)
                                .endCursor(null)
                                .build())
                .degraded(true)
                .build();
    }
}
