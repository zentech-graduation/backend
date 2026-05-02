package com.app.common.response;

import java.util.List;

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
        private String startCursor;
        private String endCursor;
    }

    /**
     * Build a forward-paginated response. {@code hasNextPage} is inferred by comparing the size of
     * {@code content} against the requested {@code limit}; callers must pass the unsliced result.
     *
     * @param content items returned for this page
     * @param limit requested page size
     * @param startCursor opaque cursor for the first item; null when content is empty
     * @param endCursor opaque cursor for the last item; null when content is empty
     * @param hasPreviousPage whether a previous page exists relative to the caller's cursor
     */
    public static <T> CursorPageResponse<T> of(
            List<T> content,
            int limit,
            String startCursor,
            String endCursor,
            boolean hasPreviousPage) {
        boolean hasNextPage = content.size() == limit;
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
