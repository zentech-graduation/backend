package com.app.common.security.service;

/** Sliding-window rate limiter for sensitive endpoints, backed by Redis. */
public interface RateLimiterService {

    /**
     * Records this request in a sliding window and returns whether it is permitted. Any window of
     * {@code windowSeconds} length will contain at most {@code maxAttempts} admitted requests.
     *
     * @param key fully-qualified bucket identifier (caller is responsible for namespacing)
     * @param maxAttempts maximum number of requests permitted within any window of length {@code
     *     windowSeconds}
     * @param windowSeconds sliding window length in seconds
     * @return {@code true} when the request is allowed, {@code false} when the limit has been
     *     exceeded
     */
    boolean isAllowed(String key, int maxAttempts, long windowSeconds);
}
