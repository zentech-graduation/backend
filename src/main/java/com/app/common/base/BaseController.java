package com.app.common.base;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.app.common.enums.ApiErrorCode;
import com.app.common.response.ApiResponse;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;

/** Base controller providing resilience fallback methods for rate limiting and circuit breaking. */
public abstract class BaseController {

    /**
     * Fallback method for rate limiter. Only handles RequestNotPermitted exceptions. Business
     * exceptions will propagate to GlobalExceptionHandler.
     */
    public ResponseEntity<ApiResponse<?>> rateLimit(RequestNotPermitted ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, "30")
                .body(ApiResponse.failure(ApiErrorCode.TOO_MANY_REQUESTS));
    }

    /**
     * Fallback method for circuit breaker. Only handles CallNotPermittedException. Business
     * exceptions will propagate to GlobalExceptionHandler.
     */
    public ResponseEntity<ApiResponse<?>> circuitBreaker(CallNotPermittedException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.failure(ApiErrorCode.SERVICE_UNAVAILABLE));
    }
}
