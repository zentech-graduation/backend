package com.app.modules.recommendation.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.app.modules.recommendation.entity.UserEvent;
import com.app.modules.recommendation.enums.UserEventType;

public interface UserEventRepositoryCustom {

    /**
     * Reads one keyset page of behavioural events inside a bounded time window.
     *
     * <p>The window is mandatory and is what makes the read affordable: {@code user_events} is
     * partitioned by {@code created_at}, so a query bounded on that column is pruned to the
     * partitions the window overlaps, while one bounded only by {@code user_id} would read every
     * partition that has ever existed.
     *
     * @param userId restrict to one account, or null for every account in the window
     * @param from inclusive lower bound of the window
     * @param to exclusive upper bound of the window
     * @param eventType restrict to one event type, or null for all of them
     * @param cursorCreatedAt keyset position timestamp, null for the first page
     * @param cursorId keyset position tiebreaker, null for the first page
     * @param limit maximum rows to read, normally the page size plus one
     * @return events ordered newest first, tiebroken by descending identifier
     */
    List<UserEvent> findPage(
            UUID userId,
            OffsetDateTime from,
            OffsetDateTime to,
            UserEventType eventType,
            OffsetDateTime cursorCreatedAt,
            UUID cursorId,
            int limit);
}
