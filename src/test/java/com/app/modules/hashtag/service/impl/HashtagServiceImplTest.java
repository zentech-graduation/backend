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
    void normalize_nameAtMaxLength_isKept() {
        String name = "a".repeat(100);
        assertThat(service.normalize("#" + name)).isEqualTo(name);
    }

    @Test
    void normalize_nameExceedingMaxLength_returnsEmpty() {
        assertThat(service.normalize("#" + "a".repeat(101))).isEmpty();
        assertThat(service.normalize("a".repeat(150))).isEmpty();
    }

    // U+20000 (CJK Unified Ideograph Extension B, first codepoint) is a supplementary-plane
    // letter: two UTF-16 code units ("𠀀") per codepoint, category Lo so toLowerCase is
    // a no-op. Used to prove the 100-character bound counts code points, not UTF-16 units.
    private static final String SUPPLEMENTARY_LETTER = "𠀀";

    @Test
    void normalize_supplementaryPlaneAtMaxCodePoints_isKept() {
        String name = SUPPLEMENTARY_LETTER.repeat(100);
        assertThat(name.codePointCount(0, name.length())).isEqualTo(100);

        assertThat(service.normalize("#" + name)).isEqualTo(name);
    }

    @Test
    void normalize_supplementaryPlaneExceedingMaxCodePoints_returnsEmpty() {
        String name = SUPPLEMENTARY_LETTER.repeat(101);
        assertThat(name.codePointCount(0, name.length())).isEqualTo(101);

        assertThat(service.normalize("#" + name)).isEmpty();
    }

    @Test
    void upsertHashtagsForPost_overlongTag_isSkipped() {
        UUID postId = UUID.randomUUID();
        Hashtag hashtag = Hashtag.builder().id(UUID.randomUUID()).name("spring").build();
        when(hashtagRepository.findByName("spring")).thenReturn(Optional.of(hashtag));

        service.upsertHashtagsForPost(postId, List.of("#Spring", "#" + "x".repeat(150)));

        verify(hashtagRepository, times(1)).upsertByName("spring");
        verify(hashtagRepository, times(1)).upsertByName(any());
        verify(postHashtagRepository, times(1)).save(any(PostHashtag.class));
    }

    @Test
    void upsertHashtagsForPost_caseDifferingDuplicates_insertsOnce() {
        UUID postId = UUID.randomUUID();
        Hashtag hashtag = Hashtag.builder().id(UUID.randomUUID()).name("spring").build();
        when(hashtagRepository.findByName("spring")).thenReturn(Optional.of(hashtag));

        service.upsertHashtagsForPost(postId, List.of("#Spring", "spring", "SPRING"));

        verify(hashtagRepository, times(1)).upsertByName("spring");
        verify(postHashtagRepository, times(1)).save(any(PostHashtag.class));
    }

    @Test
    void upsertHashtagsForPost_newTags_enqueuesUpsertEvent() {
        UUID postId = UUID.randomUUID();
        UUID hashtagId = UUID.randomUUID();
        Hashtag hashtag = Hashtag.builder().id(hashtagId).name("spring").build();
        when(hashtagRepository.findByName("spring")).thenReturn(Optional.of(hashtag));

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
        HashtagIndexProjection projection = projection(hashtagId, 2);
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

    private static HashtagIndexProjection projection(UUID id, int postCount) {
        HashtagIndexProjection projection = org.mockito.Mockito.mock(HashtagIndexProjection.class);
        when(projection.getId()).thenReturn(id);
        when(projection.getPostCount()).thenReturn(postCount);
        return projection;
    }
}
