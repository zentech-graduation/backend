package com.app.modules.recommendation.service;

import java.util.List;
import java.util.UUID;

import com.app.common.response.UserListItemResponse;

/** People you may know: the read surface, the dismissal, and the blend behind them. */
public interface SuggestionService {

    /**
     * The caller's suggestions, filtered at read time.
     *
     * <p>Falls back to the verified cold-start list when the precomputed list yields nothing, which
     * covers a brand new account and an account whose entire precomputed list has since been
     * followed, blocked or dismissed. Both are the same situation from the reader's point of view:
     * there is nothing personalised to show yet.
     *
     * @param viewerId the account reading
     * @param limit maximum rows, bounded by the service
     * @return suggested accounts with the viewer's relationship to each
     */
    List<UserListItemResponse> suggestionsFor(UUID viewerId, int limit);

    /**
     * Permanently removes one account from the caller's suggestions.
     *
     * <p>Not a block. The dismissed account is unaffected everywhere else, and is never told.
     *
     * @param viewerId the account dismissing
     * @param dismissedId the account to stop offering
     */
    void dismiss(UUID viewerId, UUID dismissedId);

    /**
     * Rebuilds one account's precomputed suggestion list.
     *
     * <p>Exposed rather than private to the job so a test can drive one account deterministically
     * instead of running the whole sweep.
     *
     * @param viewerId the account to rebuild for
     * @return how many rows the rebuild wrote
     */
    int rebuildFor(UUID viewerId);
}
