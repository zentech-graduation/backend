package com.app.modules.comment.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.app.common.response.UserSummaryResponse;
import com.app.modules.comment.dto.response.CommentBroadcastResponse;
import com.app.modules.comment.dto.response.CommentResponse;
import com.app.modules.comment.entity.Comment;
import com.app.modules.comment.mapper.CommentMapper;
import com.app.modules.comment.repository.CommentRepository;
import com.app.modules.users.service.UserSummaryService;

import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class CommentCacheServiceImplTest {

    @Mock private StringRedisTemplate redisTemplate;
    @Mock private ListOperations<String, String> listOperations;
    @Mock private CommentRepository commentRepository;
    @Mock private CommentMapper mapper;
    @Mock private UserSummaryService userSummaryService;

    private ObjectMapper objectMapper;
    private CommentCacheServiceImpl service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service =
                new CommentCacheServiceImpl(
                        redisTemplate, objectMapper, commentRepository, mapper, userSummaryService);
    }

    @Test
    void pushToFront_serializedPayload_hasNoIsLikedKey() {
        when(redisTemplate.opsForList()).thenReturn(listOperations);
        UUID postId = UUID.randomUUID();
        CommentResponse response = mockResponse(true);
        CommentBroadcastResponse broadcast = mockBroadcastResponse();
        when(mapper.toBroadcastResponse(response)).thenReturn(broadcast);

        service.pushToFront(postId, response);

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(listOperations).leftPush(anyString(), jsonCaptor.capture());
        assertThat(jsonCaptor.getValue()).doesNotContain("isLiked");
    }

    @Test
    void getOrRebuild_cacheMiss_rebuiltPayloadHasNoIsLikedKey() {
        when(redisTemplate.opsForList()).thenReturn(listOperations);
        UUID postId = UUID.randomUUID();
        when(listOperations.range(anyString(), any(Long.class), any(Long.class)))
                .thenReturn(List.of());
        Comment comment =
                Comment.builder()
                        .id(UUID.randomUUID())
                        .postId(postId)
                        .userId(UUID.randomUUID())
                        .content("hello")
                        .depth((short) 0)
                        .build();
        when(commentRepository.findFirstTopLevel(
                        org.mockito.ArgumentMatchers.eq(postId), any(PageRequest.class)))
                .thenReturn(List.of(comment));
        when(userSummaryService.loadSummaries(any())).thenReturn(java.util.Map.of());
        CommentResponse response = mockResponse(false);
        when(mapper.toResponse(any(), any(), org.mockito.ArgumentMatchers.eq(false)))
                .thenReturn(response);
        CommentBroadcastResponse broadcast = mockBroadcastResponse();
        when(mapper.toBroadcastResponse(response)).thenReturn(broadcast);

        List<CommentBroadcastResponse> result = service.getOrRebuild(postId);

        assertThat(result).containsExactly(broadcast);
        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(listOperations).rightPush(anyString(), jsonCaptor.capture());
        assertThat(jsonCaptor.getValue()).doesNotContain("isLiked");
    }

    private static CommentResponse mockResponse(boolean isLiked) {
        return new CommentResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                new UserSummaryResponse(UUID.randomUUID(), "user", "User", null, false),
                null,
                null,
                (short) 0,
                "hello",
                0,
                isLiked,
                0,
                OffsetDateTime.now(ZoneOffset.UTC),
                OffsetDateTime.now(ZoneOffset.UTC),
                false);
    }

    private static CommentBroadcastResponse mockBroadcastResponse() {
        return new CommentBroadcastResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                new UserSummaryResponse(UUID.randomUUID(), "user", "User", null, false),
                null,
                null,
                (short) 0,
                "hello",
                0,
                0,
                OffsetDateTime.now(ZoneOffset.UTC),
                OffsetDateTime.now(ZoneOffset.UTC));
    }
}
