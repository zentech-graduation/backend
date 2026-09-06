package com.app.modules.users.service;

import java.util.UUID;

import com.app.common.response.CursorPageResponse;
import com.app.common.response.UserListItemResponse;

public interface UserSearchService {

    /**
     * Searches live, active users by username substring, ordered by follower count.
     *
     * <p>Backed directly by the PostgreSQL {@code pg_trgm} GIN index, not Elasticsearch, and
     * deliberately carries <strong>no circuit breaker</strong>. The breaker on post and hashtag
     * search guards a remote dependency and degrades to a lower tier; here the backend is the
     * source of truth and there is no lower tier, so an open breaker would turn a database blip
     * into a silently empty result set and leave the caller unable to tell "no such user" from "we
     * lost the database". Availability failures propagate.
     *
     * <p>Matching is case-insensitive substring. The viewer is excluded from their own results.
     * Banned, suspended, deactivated, and soft-deleted accounts are never returned. Users who have
     * blocked the viewer <em>are</em> returned, carrying {@code isBlockedBy = true}: filtering them
     * out would leak the block set by omission.
     *
     * @param viewerId authenticated caller; required, since this endpoint is not anonymous
     * @param query search term, trimmed; must be at least 2 characters after trimming
     * @param cursor opaque offset cursor from the previous page; null or blank for the first page
     * @param limit requested page size, normalized to 1-100 with a default of 20
     * @return cursor page of matching users with viewer relationship state
     * @throws com.app.common.exception.AppException with {@code VALIDATION_ERROR} when the trimmed
     *     query is shorter than 2 characters, or {@code INVALID_CURSOR} for a malformed or
     *     out-of-range cursor
     */
    CursorPageResponse<UserListItemResponse> searchUsers(
            UUID viewerId, String query, String cursor, int limit);
}
