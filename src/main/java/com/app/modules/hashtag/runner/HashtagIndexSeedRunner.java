package com.app.modules.hashtag.runner;

import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.stereotype.Component;

import com.app.modules.hashtag.config.HashtagProperties;
import com.app.modules.hashtag.mapper.HashtagMapper;
import com.app.modules.hashtag.repository.HashtagRepository;
import com.app.modules.hashtag.search.HashtagDocument;
import com.app.modules.hashtag.search.HashtagSearchRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Seeds the Elasticsearch {@code hashtags} index from PostgreSQL on startup when the index is
 * empty. Controlled by {@code app.hashtag.seed.enabled}. Seeding failures are non-fatal:
 * Elasticsearch is a rebuildable secondary tier, so the application starts even if it is
 * unavailable, and search degrades to the PostgreSQL pg_trgm fallback.
 */
@Slf4j
@Component
public class HashtagIndexSeedRunner implements ApplicationRunner {

    private final HashtagRepository hashtagRepository;
    private final HashtagSearchRepository hashtagSearchRepository;
    private final ElasticsearchOperations elasticsearchOperations;
    private final HashtagMapper hashtagMapper;
    private final HashtagProperties properties;

    public HashtagIndexSeedRunner(
            HashtagRepository hashtagRepository,
            HashtagSearchRepository hashtagSearchRepository,
            ElasticsearchOperations elasticsearchOperations,
            HashtagMapper hashtagMapper,
            HashtagProperties properties) {
        this.hashtagRepository = hashtagRepository;
        this.hashtagSearchRepository = hashtagSearchRepository;
        this.elasticsearchOperations = elasticsearchOperations;
        this.hashtagMapper = hashtagMapper;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.getSeed().isEnabled()) {
            return;
        }
        try {
            long count = elasticsearchOperations.count(Query.findAll(), HashtagDocument.class);
            // Skip bootstrap if the index already has documents — partial population is also
            // skipped
            if (count > 0) {
                log.info("Hashtag index already contains {} documents; skipping seed", count);
                return;
            }
            List<HashtagDocument> documents =
                    hashtagRepository.findAll().stream().map(hashtagMapper::toDocument).toList();
            hashtagSearchRepository.saveAll(documents);
            log.info("Hashtag index seeded with {} documents", documents.size());
        } catch (Exception e) {
            // Non-fatal: never block startup on the rebuildable search tier.
            log.warn(
                    "Hashtag index seeding skipped due to Elasticsearch error: {}", e.getMessage());
        }
    }
}
