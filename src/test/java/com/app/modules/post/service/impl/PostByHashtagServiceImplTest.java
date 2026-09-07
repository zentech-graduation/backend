package com.app.modules.post.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;

import com.app.common.enums.ApiErrorCode;
import com.app.common.exception.AppException;
import com.app.common.response.CursorPageResponse;
import com.app.modules.hashtag.service.HashtagLookupService;
import com.app.modules.post.dto.response.PostResponse;
import com.app.modules.post.repository.PostRepository;
import com.app.modules.post.service.PostVisibilityService;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;

@ExtendWith(MockitoExtension.class)
class PostByHashtagServiceImplTest {

    private static final UUID VIEWER_ID = UUID.randomUUID();
    private static final UUID HASHTAG_ID = UUID.randomUUID();

    @Mock private ElasticsearchOperations elasticsearchOperations;
    @Mock private PostRepository postRepository;
    @Mock private PostVisibilityService postVisibilityService;
    @Mock private PostResponseAssembler postResponseAssembler;
    @Mock private HashtagLookupService hashtagLookupService;
    @Mock private PostByHashtagPostgresReader postgresReader;

    private PostByHashtagServiceImpl service;

    @BeforeEach
    void setUp() {
        service =
                new PostByHashtagServiceImpl(
                        elasticsearchOperations,
                        postRepository,
                        postVisibilityService,
                        postResponseAssembler,
                        hashtagLookupService,
                        postgresReader);
    }

    private static CursorPageResponse<PostResponse> emptyPage() {
        return CursorPageResponse.of(List.of(), false, null, null, false);
    }

    // An Elasticsearch transport failure is an availability failure, so the read degrades to the
    // PostgreSQL join rather than surfacing an error: post_hashtags is the source of truth for
    // hashtag membership, so the degraded answer is still complete.
    @Test
    void fallback_transportFailure_readsFromPostgres() {
        when(postgresReader.read(eq(VIEWER_ID), eq(HASHTAG_ID), eq(0), anyInt()))
                .thenReturn(emptyPage());

        CursorPageResponse<PostResponse> result =
                service.findPostsByHashtagFallback(
                        VIEWER_ID,
                        HASHTAG_ID,
                        null,
                        20,
                        new DataAccessResourceFailureException("connection refused"));

        assertThat(result).isNotNull();
        verify(postgresReader).read(eq(VIEWER_ID), eq(HASHTAG_ID), eq(0), anyInt());
    }

    @Test
    void fallback_ioFailureNestedInCause_readsFromPostgres() {
        when(postgresReader.read(eq(VIEWER_ID), eq(HASHTAG_ID), eq(0), anyInt()))
                .thenReturn(emptyPage());

        service.findPostsByHashtagFallback(
                VIEWER_ID,
                HASHTAG_ID,
                null,
                20,
                new RuntimeException("wrapped", new IOException("socket closed")));

        verify(postgresReader).read(eq(VIEWER_ID), eq(HASHTAG_ID), eq(0), anyInt());
    }

    // An open circuit means the primary method body never ran, so the lifecycle gate it normally
    // applies has not been applied yet and the fallback has to apply it itself. Without this, a
    // banned hashtag would answer 200 with posts for as long as the circuit stayed open.
    @Test
    void fallback_openCircuit_appliesLifecycleGateItself() {
        CallNotPermittedException openCircuit =
                CallNotPermittedException.createCallNotPermittedException(
                        CircuitBreaker.ofDefaults("elasticsearchSearch"));
        when(postgresReader.read(eq(VIEWER_ID), eq(HASHTAG_ID), eq(0), anyInt()))
                .thenReturn(emptyPage());

        service.findPostsByHashtagFallback(VIEWER_ID, HASHTAG_ID, null, 20, openCircuit);

        verify(hashtagLookupService).getById(HASHTAG_ID);
    }

    // The primary path already applied the gate before throwing, so re-applying it here would
    // double the read on every degraded request.
    @Test
    void fallback_primaryThrew_doesNotReapplyLifecycleGate() {
        when(postgresReader.read(eq(VIEWER_ID), eq(HASHTAG_ID), eq(0), anyInt()))
                .thenReturn(emptyPage());

        service.findPostsByHashtagFallback(
                VIEWER_ID, HASHTAG_ID, null, 20, new DataAccessResourceFailureException("down"));

        verify(hashtagLookupService, never()).getById(any());
    }

    // The lifecycle refusal raised by the primary is not an availability failure and must reach the
    // caller as a 404, not be masked by silently answering from PostgreSQL.
    @Test
    void fallback_lifecycleRefusal_rethrowsRatherThanDegrading() {
        AppException unavailable = new AppException(ApiErrorCode.HASHTAG_UNAVAILABLE);

        assertThatThrownBy(
                        () ->
                                service.findPostsByHashtagFallback(
                                        VIEWER_ID, HASHTAG_ID, null, 20, unavailable))
                .isInstanceOf(AppException.class)
                .hasFieldOrPropertyWithValue("errorCode", ApiErrorCode.HASHTAG_UNAVAILABLE);

        verify(postgresReader, never()).read(any(), any(), anyInt(), anyInt());
    }

    // A programming or data error must surface, not be reported as a successful degraded read.
    @Test
    void fallback_nonAvailabilityFailure_rethrows() {
        assertThatThrownBy(
                        () ->
                                service.findPostsByHashtagFallback(
                                        VIEWER_ID,
                                        HASHTAG_ID,
                                        null,
                                        20,
                                        new IllegalArgumentException("bug")))
                .isInstanceOf(IllegalArgumentException.class);

        verify(postgresReader, never()).read(any(), any(), anyInt(), anyInt());
    }
}
