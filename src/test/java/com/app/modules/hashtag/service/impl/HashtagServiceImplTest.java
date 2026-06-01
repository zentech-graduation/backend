package com.app.modules.hashtag.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.app.modules.hashtag.entity.Hashtag;
import com.app.modules.hashtag.entity.PostHashtag;
import com.app.modules.hashtag.mapper.HashtagMapper;
import com.app.modules.hashtag.repository.HashtagRepository;
import com.app.modules.hashtag.repository.PostHashtagRepository;
import com.app.modules.hashtag.search.HashtagDocument;
import com.app.modules.hashtag.search.HashtagSearchRepository;

@ExtendWith(MockitoExtension.class)
class HashtagServiceImplTest {

    @Mock private HashtagRepository hashtagRepository;
    @Mock private PostHashtagRepository postHashtagRepository;
    @Mock private HashtagSearchRepository hashtagSearchRepository;
    @Mock private HashtagMapper hashtagMapper;

    private HashtagServiceImpl service;

    @BeforeEach
    void setUp() {
        service =
                new HashtagServiceImpl(
                        hashtagRepository,
                        postHashtagRepository,
                        hashtagSearchRepository,
                        hashtagMapper);
    }

    @AfterEach
    void clearSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
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

        TransactionSynchronizationManager.initSynchronization();
        service.upsertHashtagsForPost(postId, List.of("#Spring", "spring", "SPRING"));

        verify(hashtagRepository, times(1)).upsertByName("spring");
        verify(postHashtagRepository, times(1)).save(any(PostHashtag.class));
    }

    @Test
    void upsertHashtagsForPost_elasticsearchWriteFails_transactionNotAborted() {
        UUID postId = UUID.randomUUID();
        Hashtag hashtag = Hashtag.builder().id(UUID.randomUUID()).name("fail").build();
        when(hashtagRepository.findByNameIgnoreCase("fail")).thenReturn(Optional.of(hashtag));
        when(hashtagMapper.toDocument(hashtag)).thenReturn(new HashtagDocument());
        doThrow(new RuntimeException("ES down")).when(hashtagSearchRepository).save(any());

        TransactionSynchronizationManager.initSynchronization();
        assertThatCode(
                        () -> {
                            service.upsertHashtagsForPost(postId, List.of("fail"));
                            TransactionSynchronizationManager.getSynchronizations()
                                    .forEach(TransactionSynchronization::afterCommit);
                        })
                .doesNotThrowAnyException();

        verify(postHashtagRepository).save(any(PostHashtag.class));
        verify(hashtagSearchRepository).save(any(HashtagDocument.class));
    }

    @Test
    void removeHashtagsForPost_anyPost_deletesAssociations() {
        UUID postId = UUID.randomUUID();
        service.removeHashtagsForPost(postId);
        verify(postHashtagRepository).deleteAllByPostId(postId);
    }
}
