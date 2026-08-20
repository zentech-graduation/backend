package com.app.modules.admin.service;

import java.time.Duration;
import java.time.OffsetDateTime;

import com.app.modules.admin.dto.response.AdminStatsCurrentResponse;
import com.app.modules.admin.dto.response.AdminStatsTimeseriesResponse;

/** Administrative read access to platform statistics. */
public interface AdminStatsService {

    /** Window used when a caller supplies neither bound. */
    Duration DEFAULT_TIMESERIES_WINDOW = Duration.ofHours(24);

    /** Longest window a single series read may span. */
    Duration MAX_TIMESERIES_WINDOW = Duration.ofDays(365);

    /**
     * Returns the newest stored snapshot without computing any of it.
     *
     * <p>Reads the most recent collected bucket. It deliberately issues no aggregate over {@code
     * users}, {@code posts} or {@code comments}: at production size each of those is a scan of
     * millions of index entries and takes seconds, which the collection job absorbs where seconds
     * do not matter. The only figure computed at request time is the most-used hashtag list, which
     * an index serves as a scan with a limit and which is flagged as live in the response.
     *
     * @return the newest snapshot, with null timestamps and empty breakdowns when nothing has been
     *     collected yet
     */
    AdminStatsCurrentResponse getCurrent();

    /**
     * Returns one metric's stored series over a window.
     *
     * <p>Granularity is decided here rather than requested: fine buckets survive only inside the
     * fine retention window, so a window reaching further back is served from the rolled-up daily
     * rows. The choice is stated in the response.
     *
     * @param metric metric key to read
     * @param from inclusive lower bound; defaults with {@code to} to the last {@link
     *     #DEFAULT_TIMESERIES_WINDOW} when both are absent
     * @param to exclusive upper bound
     * @return the series, with the granularity that was used
     * @throws com.app.common.exception.AppException {@code BAD_REQUEST} when the metric is unknown,
     *     when exactly one bound is supplied, when {@code to} is not after {@code from}, or when
     *     the window exceeds {@link #MAX_TIMESERIES_WINDOW}
     */
    AdminStatsTimeseriesResponse getTimeseries(
            String metric, OffsetDateTime from, OffsetDateTime to);
}
