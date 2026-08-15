package com.app.modules.comment.enums;

import com.app.common.enums.ApiErrorCode;
import com.app.common.exception.AppException;
import com.app.common.pagination.CursorScope;

/**
 * Sort modes for a post's top-level comment list.
 *
 * <p>Both modes return the same chronological ordering, newest first. They differ only in whether
 * the pinned block of most-liked comments is computed, prepended to the first page, and excluded
 * from the chronological body. Neither mode ranks the paginated stream by like count: {@code
 * like_count} is mutable, so a keyset over it would lose and duplicate rows as counts change
 * between page requests.
 *
 * <p>Each mode carries its own cursor scope because the two return different row sets for the same
 * position: {@code TOP} omits the pinned comments from its body and {@code NEWEST} does not.
 * Replaying a cursor across modes therefore fails the scope check in {@link
 * com.app.common.pagination.CursorCodec} rather than silently skipping or repeating rows.
 */
public enum CommentSortOrder {

    /** Today's behaviour, and the default: pinned block first, then the remainder. */
    TOP("top", CursorScope.COMMENTS_TOP_LEVEL, true),

    /** Pure chronology: nothing prepended and nothing excluded. */
    NEWEST("newest", CursorScope.COMMENTS_TOP_LEVEL_NEWEST, false);

    private final String wireName;
    private final String cursorScope;
    private final boolean pinned;

    CommentSortOrder(String wireName, String cursorScope, boolean pinned) {
        this.wireName = wireName;
        this.cursorScope = cursorScope;
        this.pinned = pinned;
    }

    /**
     * Resolves the query parameter value, defaulting to {@link #TOP} when it is absent.
     *
     * <p>An unrecognised value is rejected rather than falling back to the default: a silent
     * fallback turns a client-side typo into a sort control that appears to do nothing.
     *
     * @param value raw query parameter; null or blank means the parameter was not supplied
     * @return the requested mode
     * @throws AppException with {@link ApiErrorCode#BAD_REQUEST} when the value is not one of the
     *     accepted names
     */
    public static CommentSortOrder from(String value) {
        if (value == null || value.isBlank()) {
            return TOP;
        }
        for (CommentSortOrder order : values()) {
            if (order.wireName.equals(value)) {
                return order;
            }
        }
        throw new AppException(
                ApiErrorCode.BAD_REQUEST,
                "Unknown sort value; accepted values are 'top' and 'newest'");
    }

    /** Returns the cursor scope tag this mode issues and accepts. */
    public String cursorScope() {
        return cursorScope;
    }

    /** Returns whether this mode computes a pinned block and excludes it from the body. */
    public boolean pinsTopComments() {
        return pinned;
    }
}
