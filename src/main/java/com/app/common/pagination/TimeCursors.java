package com.app.common.pagination;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Converts between {@link OffsetDateTime} sort values and the epoch-microsecond longs carried in a
 * {@link Cursor}.
 *
 * <p>Microseconds match PostgreSQL {@code timestamptz} granularity, so the round trip is lossless
 * for values stored in the database and removes the timezone and precision ambiguity of an ISO-8601
 * cursor.
 */
public final class TimeCursors {

    private static final long MICROS_PER_SECOND = 1_000_000L;
    private static final long NANOS_PER_MICRO = 1_000L;

    private TimeCursors() {}

    /**
     * Converts a timestamp to epoch microseconds.
     *
     * @param time timestamp to convert; must not be null
     * @return microseconds since the epoch, truncated to microsecond resolution
     */
    public static long toMicros(OffsetDateTime time) {
        Instant instant = time.toInstant();
        return instant.getEpochSecond() * MICROS_PER_SECOND + instant.getNano() / NANOS_PER_MICRO;
    }

    /**
     * Converts epoch microseconds back to a UTC timestamp.
     *
     * @param micros microseconds since the epoch
     * @return the timestamp at UTC offset
     */
    public static OffsetDateTime fromMicros(long micros) {
        long seconds = Math.floorDiv(micros, MICROS_PER_SECOND);
        long microOfSecond = Math.floorMod(micros, MICROS_PER_SECOND);
        return OffsetDateTime.ofInstant(
                Instant.ofEpochSecond(seconds, microOfSecond * NANOS_PER_MICRO), ZoneOffset.UTC);
    }
}
