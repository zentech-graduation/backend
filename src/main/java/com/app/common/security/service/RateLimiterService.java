package com.app.common.security.service;

/** Sliding-window rate limiter for sensitive endpoints, backed by Redis. */
public interface RateLimiterService {

    /**
     * Atomically increments the counter for the supplied key and returns whether the request is
     * permitted. The window TTL is established on the first request only.
     *
     * @param key fully-qualified bucket identifier (caller is responsible for namespacing)
     * @param maxAttempts maximum number of requests permitted within the window
     * @param windowSeconds window length in seconds
     * @return {@code true} when the request is allowed, {@code false} when the limit has been
     *     exceeded
     */
    boolean isAllowed(String key, int maxAttempts, long windowSeconds);
}
