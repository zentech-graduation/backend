package com.app.common.base;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.app.common.enums.ApiErrorCode;
import com.app.common.response.ApiResponse;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;

class BaseControllerTest {

    private BaseController controller;

    @BeforeEach
    void setUp() {
        controller =
                new BaseController() {
                    // concrete subclass — no additional methods needed
                };
    }

    @Test
    void rateLimit_requestNotPermitted_returns429WithRetryAfterHeader() {
        RateLimiter limiter = RateLimiter.ofDefaults("test");
        RequestNotPermitted ex = RequestNotPermitted.createRequestNotPermitted(limiter);

        ResponseEntity<ApiResponse<?>> response = controller.rateLimit(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("30");
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode())
                .isEqualTo(ApiErrorCode.TOO_MANY_REQUESTS.getCode());
    }

    @Test
    void circuitBreaker_callNotPermitted_returns503() {
        CircuitBreaker cb = CircuitBreaker.ofDefaults("test");
        CallNotPermittedException ex =
                CallNotPermittedException.createCallNotPermittedException(cb);

        ResponseEntity<ApiResponse<?>> response = controller.circuitBreaker(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode())
                .isEqualTo(ApiErrorCode.SERVICE_UNAVAILABLE.getCode());
    }
}
