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

    /**
     * Reads up to {@code limit} of a user's most recent {@code entity_id} values for one event type
     * inside a bounded window, newest first.
     *
     * <p>The window is mandatory for the same reason it is on {@link #findPage} - {@code
     * user_events} is partitioned on {@code created_at}, and an unbounded read touches every
     * partition ever declared. Unlike {@link #findPage}, this projects only the entity id: it
     * exists to build an in-memory read-set for the recommendation topup, not to render events, so
     * loading full rows would be wasted work. The result may repeat an id (a post viewed more than
     * once inside the window produces one row per view); the caller collects into a {@code Set}.
     *
     * @param userId account whose events are read
     * @param eventType restrict to one event type, e.g. {@link UserEventType#POST_VIEW}
     * @param from inclusive lower bound of the window
     * @param to exclusive upper bound of the window
     * @param limit maximum rows to read
     * @return entity ids newest first, possibly containing duplicates, capped at {@code limit}
     */
    List<UUID> findRecentEntityIds(
            UUID userId,
            UserEventType eventType,
            OffsetDateTime from,
            OffsetDateTime to,
            int limit);
}
