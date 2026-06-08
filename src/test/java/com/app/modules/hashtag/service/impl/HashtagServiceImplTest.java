package com.app.modules.hashtag.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.app.common.outbox.service.OutboxService;
import com.app.modules.hashtag.entity.Hashtag;
import com.app.modules.hashtag.entity.PostHashtag;
import com.app.modules.hashtag.entity.PostHashtagId;
import com.app.modules.hashtag.repository.HashtagIndexProjection;
import com.app.modules.hashtag.repository.HashtagRepository;
import com.app.modules.hashtag.repository.PostHashtagRepository;

@ExtendWith(MockitoExtension.class)
class HashtagServiceImplTest {

    @Mock private HashtagRepository hashtagRepository;
    @Mock private PostHashtagRepository postHashtagRepository;
    @Mock private OutboxService outboxService;
    @Mock private EntityManager entityManager;

    private HashtagServiceImpl service;

    @BeforeEach
    void setUp() {
        service =
                new HashtagServiceImpl(
                        hashtagRepository, postHashtagRepository, entityManager, outboxService);
    }

    @Test
    void normalize_variousInputs_stripsHashLowercasesTrims() {
        assertThat(service.normalize("#Spring ")).isEqualTo("spring");
        assertThat(service.normalize("  JAVA")).isEqualTo("java");
        assertThat(service.normalize("already")).isEqualTo("already");
        assertThat(service.normalize(" #Spring ")).isEqualTo("spring");
    }

    @Test
    void upsertHashtagsForPost_caseDifferingDuplicates_insertsOnce() {
        UUID postId = UUID.randomUUID();
        Hashtag hashtag = Hashtag.builder().id(UUID.randomUUID()).name("spring").build();
        when(hashtagRepository.findByNameIgnoreCase("spring")).thenReturn(Optional.of(hashtag));

        service.upsertHashtagsForPost(postId, List.of("#Spring", "spring", "SPRING"));

        verify(hashtagRepository, times(1)).upsertByName("spring");
        verify(postHashtagRepository, times(1)).save(any(PostHashtag.class));
    }

    @Test
    void upsertHashtagsForPost_newTags_enqueuesUpsertEvent() {
        UUID postId = UUID.randomUUID();
        UUID hashtagId = UUID.randomUUID();
        Hashtag hashtag = Hashtag.builder().id(hashtagId).name("spring").build();
        when(hashtagRepository.findByNameIgnoreCase("spring")).thenReturn(Optional.of(hashtag));
        HashtagIndexProjection projection = projection(hashtagId, "spring", 3);
        when(hashtagRepository.findIndexProjectionsByIdIn(any())).thenReturn(List.of(projection));

        service.upsertHashtagsForPost(postId, List.of("#Spring"));

        verify(outboxService)
                .enqueue(
                        eq("hashtag.index.upsert.v1"),
                        eq("hashtag.index.upsert.v1"),
                        eq("hashtag"),
                        any(UUID.class),
                        isNull(),
                        anyMap());
    }

    @Test
    void removeHashtagsForPost_lastAssociation_enqueuesDeleteEvent() {
        UUID postId = UUID.randomUUID();
        UUID hashtagId = UUID.randomUUID();
        when(postHashtagRepository.findHashtagIdsByPostId(postId)).thenReturn(List.of(hashtagId));
        when(hashtagRepository.findIndexProjectionsByIdIn(List.of(hashtagId)))
                .thenReturn(List.of());

        service.removeHashtagsForPost(postId);

        verify(postHashtagRepository).deleteAllByPostId(postId);
        verify(outboxService)
                .enqueue(
                        eq("hashtag.index.delete.v1"),
                        eq("hashtag.index.delete.v1"),
                        eq("hashtag"),
                        eq(hashtagId),
                        isNull(),
                        anyMap());
    }

    @Test
    void removeHashtagsForPost_remainingAssociations_enqueuesUpsertEvent() {
        UUID postId = UUID.randomUUID();
        UUID hashtagId = UUID.randomUUID();
        when(postHashtagRepository.findHashtagIdsByPostId(postId)).thenReturn(List.of(hashtagId));
        HashtagIndexProjection projection = projection(hashtagId, "spring", 2);
        when(hashtagRepository.findIndexProjectionsByIdIn(List.of(hashtagId)))
                .thenReturn(List.of(projection));

        service.removeHashtagsForPost(postId);

        verify(postHashtagRepository).deleteAllByPostId(postId);
        verify(outboxService)
                .enqueue(
                        eq("hashtag.index.upsert.v1"),
                        eq("hashtag.index.upsert.v1"),
                        eq("hashtag"),
                        eq(hashtagId),
                        isNull(),
                        anyMap());
    }

    @Test
    void getHashtagIdsForPosts_multiplePosts_groupsByPostId() {
        UUID postA = UUID.randomUUID();
        UUID postB = UUID.randomUUID();
        UUID tag1 = UUID.randomUUID();
        UUID tag2 = UUID.randomUUID();
        UUID tag3 = UUID.randomUUID();
        when(postHashtagRepository.findAllByIdPostIdIn(List.of(postA, postB)))
                .thenReturn(
                        List.of(
                                PostHashtag.builder().id(new PostHashtagId(postA, tag1)).build(),
                                PostHashtag.builder().id(new PostHashtagId(postA, tag2)).build(),
                                PostHashtag.builder().id(new PostHashtagId(postB, tag3)).build()));

        Map<UUID, List<UUID>> result = service.getHashtagIdsForPosts(List.of(postA, postB));

        assertThat(result).containsOnlyKeys(postA, postB);
        assertThat(result.get(postA)).containsExactlyInAnyOrder(tag1, tag2);
        assertThat(result.get(postB)).containsExactly(tag3);
    }

    @Test
    void getHashtagIdsForPosts_emptyInput_returnsEmptyMap() {
        assertThat(service.getHashtagIdsForPosts(List.of())).isEmpty();
        verifyNoInteractions(postHashtagRepository);
    }

    private static HashtagIndexProjection projection(UUID id, String name, int postCount) {
        HashtagIndexProjection projection = org.mockito.Mockito.mock(HashtagIndexProjection.class);
        when(projection.getId()).thenReturn(id);
        when(projection.getName()).thenReturn(name);
        when(projection.getPostCount()).thenReturn(postCount);
        when(projection.getCreatedAt()).thenReturn(OffsetDateTime.now());
        return projection;
    }
}
