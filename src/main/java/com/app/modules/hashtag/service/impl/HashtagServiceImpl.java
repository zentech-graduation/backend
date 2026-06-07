package com.app.modules.hashtag.service.impl;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.app.modules.hashtag.entity.Hashtag;
import com.app.modules.hashtag.entity.PostHashtag;
import com.app.modules.hashtag.entity.PostHashtagId;
import com.app.modules.hashtag.mapper.HashtagMapper;
import com.app.modules.hashtag.repository.HashtagRepository;
import com.app.modules.hashtag.repository.PostHashtagRepository;
import com.app.modules.hashtag.search.HashtagSearchRepository;
import com.app.modules.hashtag.service.HashtagService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class HashtagServiceImpl implements HashtagService {

    private final HashtagRepository hashtagRepository;
    private final PostHashtagRepository postHashtagRepository;
    private final HashtagSearchRepository hashtagSearchRepository;
    private final HashtagMapper hashtagMapper;

    public HashtagServiceImpl(
            HashtagRepository hashtagRepository,
            PostHashtagRepository postHashtagRepository,
            HashtagSearchRepository hashtagSearchRepository,
            HashtagMapper hashtagMapper) {
        this.hashtagRepository = hashtagRepository;
        this.postHashtagRepository = postHashtagRepository;
        this.hashtagSearchRepository = hashtagSearchRepository;
        this.hashtagMapper = hashtagMapper;
    }

    @Override
    public String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.strip();
        String withoutHash = trimmed.replaceFirst("^#+", "");
        // Locale.ROOT lowercasing keeps the unique-index key stable across server locales.
        return withoutHash.strip().toLowerCase(Locale.ROOT);
    }

    @Override
    @Transactional
    public void upsertHashtagsForPost(UUID postId, List<String> rawTags) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        for (String raw : rawTags) {
            String n = normalize(raw);
            if (!n.isBlank()) {
                names.add(n);
            }
        }

        for (String name : names) {
            hashtagRepository.upsertByName(name);
            Hashtag hashtag = hashtagRepository.findByNameIgnoreCase(name).orElseThrow();
            postHashtagRepository.save(
                    PostHashtag.builder().id(new PostHashtagId(postId, hashtag.getId())).build());

            final Hashtag committed = hashtag;
            // Post-commit dual-write: index in Elasticsearch only after the DB transaction commits.
            // ES failure must never roll back the source-of-truth write, so it is caught and
            // logged.
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            try {
                                hashtagSearchRepository.save(hashtagMapper.toDocument(committed));
                            } catch (Exception e) {
                                log.warn(
                                        "Elasticsearch dual-write failed for hashtag '{}': {}",
                                        committed.getName(),
                                        e.getMessage());
                            }
                        }
                    });
        }
    }

    @Override
    @Transactional
    public void removeHashtagsForPost(UUID postId) {
        postHashtagRepository.deleteAllByPostId(postId);
    }
}
