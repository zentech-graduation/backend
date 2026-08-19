package com.app.modules.hashtag.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.app.common.enums.ApiErrorCode;
import com.app.common.exception.AppException;
import com.app.modules.hashtag.entity.Hashtag;
import com.app.modules.hashtag.enums.HashtagStatus;
import com.app.modules.hashtag.repository.HashtagRepository;
import com.app.modules.hashtag.repository.HashtagTrendingRepository;
import com.app.modules.hashtag.service.HashtagIndexEventPublisher;
import com.app.modules.hashtag.service.HashtagLifecycleResult;
import com.app.modules.hashtag.service.HashtagService;

@ExtendWith(MockitoExtension.class)
class HashtagLifecycleServiceImplTest {

    @Mock private HashtagRepository hashtagRepository;
    @Mock private EntityManager entityManager;
    @Mock private HashtagTrendingRepository hashtagTrendingRepository;
    @Mock private HashtagIndexEventPublisher indexEventPublisher;
    @Mock private HashtagService hashtagService;

    private HashtagLifecycleServiceImpl service;

    @BeforeEach
    void setUp() {
        service =
                new HashtagLifecycleServiceImpl(
                        hashtagRepository,
                        entityManager,
                        hashtagTrendingRepository,
                        hashtagService,
                        indexEventPublisher);
    }

    @Test
    void changeStatus_activeToBanned_movesTheRowAndRecordsTheDecision() {
        UUID actorId = UUID.randomUUID();
        Hashtag hashtag = hashtag(HashtagStatus.ACTIVE);
        when(hashtagRepository.findById(hashtag.getId())).thenReturn(Optional.of(hashtag));
        when(hashtagTrendingRepository.deleteAllByHashtagId(hashtag.getId())).thenReturn(3);

        HashtagLifecycleResult result =
                service.changeStatus(actorId, hashtag.getId(), HashtagStatus.BANNED, "spam wave");

        assertThat(result.previousStatus()).isEqualTo(HashtagStatus.ACTIVE);
        assertThat(result.status()).isEqualTo(HashtagStatus.BANNED);
        assertThat(result.purgedTrendingRows()).isEqualTo(3);
        assertThat(hashtag.getStatus()).isEqualTo(HashtagStatus.BANNED);
        assertThat(hashtag.getStatusNote()).isEqualTo("spam wave");
        assertThat(hashtag.getStatusBy()).isEqualTo(actorId);
        assertThat(hashtag.getStatusAt()).isNotNull();
        verify(indexEventPublisher).enqueueSync(hashtag.getId());
    }

    @Test
    void changeStatus_everyDistinctPairIsAccepted() {
        for (HashtagStatus from : HashtagStatus.values()) {
            for (HashtagStatus to : HashtagStatus.values()) {
                if (from == to) {
                    continue;
                }
                Hashtag hashtag = hashtag(from);
                when(hashtagRepository.findById(hashtag.getId())).thenReturn(Optional.of(hashtag));

                HashtagLifecycleResult result =
                        service.changeStatus(UUID.randomUUID(), hashtag.getId(), to, "note");

                assertThat(result.status()).as("%s to %s", from, to).isEqualTo(to);
                assertThat(result.previousStatus()).as("%s to %s", from, to).isEqualTo(from);
            }
        }
    }

    @Test
    void changeStatus_sameStatus_isRefusedForEveryValue() {
        for (HashtagStatus status : HashtagStatus.values()) {
            Hashtag hashtag = hashtag(status);
            when(hashtagRepository.findById(hashtag.getId())).thenReturn(Optional.of(hashtag));

            assertThatThrownBy(
                            () ->
                                    service.changeStatus(
                                            UUID.randomUUID(), hashtag.getId(), status, "note"))
                    .as("%s to itself", status)
                    .isInstanceOf(AppException.class)
                    .extracting(ex -> ((AppException) ex).getErrorCode())
                    .isEqualTo(ApiErrorCode.ADMIN_INVALID_TRANSITION);
        }
        verify(hashtagRepository, never()).save(any());
        verifyNoInteractions(hashtagTrendingRepository);
        verifyNoInteractions(indexEventPublisher);
    }

    @Test
    void changeStatus_backToActive_purgesNoTrendingRows() {
        Hashtag hashtag = hashtag(HashtagStatus.BANNED);
        when(hashtagRepository.findById(hashtag.getId())).thenReturn(Optional.of(hashtag));

        HashtagLifecycleResult result =
                service.changeStatus(
                        UUID.randomUUID(), hashtag.getId(), HashtagStatus.ACTIVE, "cleared");

        assertThat(result.purgedTrendingRows()).isZero();
        verifyNoInteractions(hashtagTrendingRepository);
        verify(indexEventPublisher).enqueueSync(hashtag.getId());
    }

    @Test
    void changeStatus_missingHashtag_throwsNotFound() {
        UUID hashtagId = UUID.randomUUID();
        when(hashtagRepository.findById(hashtagId)).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                service.changeStatus(
                                        UUID.randomUUID(), hashtagId, HashtagStatus.BANNED, "note"))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ApiErrorCode.HASHTAG_NOT_FOUND);
    }

    @Test
    void create_deletedStatus_isRefused() {
        when(hashtagService.normalize("#gone")).thenReturn("gone");

        assertThatThrownBy(
                        () ->
                                service.create(
                                        UUID.randomUUID(), "#gone", HashtagStatus.DELETED, "note"))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ApiErrorCode.BAD_REQUEST);
        verifyNoInteractions(hashtagRepository);
    }

    @Test
    void create_nameThatNormalizesToNothing_isRefused() {
        when(hashtagService.normalize("###")).thenReturn("");

        assertThatThrownBy(
                        () ->
                                service.create(
                                        UUID.randomUUID(), "###", HashtagStatus.BANNED, "note"))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ApiErrorCode.BAD_REQUEST);
        verifyNoInteractions(hashtagRepository);
    }

    @Test
    void create_bannedTerm_insertsNormalizedAndEnqueuesNoIndexEvent() {
        UUID actorId = UUID.randomUUID();
        when(hashtagService.normalize("#WorldCup")).thenReturn("worldcup");

        HashtagLifecycleResult result =
                service.create(actorId, "#WorldCup", HashtagStatus.BANNED, "ahead of the event");

        assertThat(result.name()).isEqualTo("worldcup");
        assertThat(result.previousStatus()).isNull();
        assertThat(result.status()).isEqualTo(HashtagStatus.BANNED);
        ArgumentCaptor<Hashtag> saved = ArgumentCaptor.forClass(Hashtag.class);
        verify(hashtagRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getName()).isEqualTo("worldcup");
        assertThat(saved.getValue().getStatus()).isEqualTo(HashtagStatus.BANNED);
        assertThat(saved.getValue().getStatusNote()).isEqualTo("ahead of the event");
        assertThat(saved.getValue().getStatusBy()).isEqualTo(actorId);
        verifyNoInteractions(indexEventPublisher);
    }

    private static Hashtag hashtag(HashtagStatus status) {
        return Hashtag.builder().id(UUID.randomUUID()).name("tag").status(status).build();
    }
}
