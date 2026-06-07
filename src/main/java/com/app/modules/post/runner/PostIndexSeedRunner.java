package com.app.modules.post.runner;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.stereotype.Component;

import com.app.modules.hashtag.service.HashtagService;
import com.app.modules.post.config.PostProperties;
import com.app.modules.post.entity.Post;
import com.app.modules.post.enums.PostStatus;
import com.app.modules.post.mapper.PostMapper;
import com.app.modules.post.repository.PostRepository;
import com.app.modules.post.search.PostDocument;
import com.app.modules.post.search.PostSearchRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * Seeds the Elasticsearch {@code posts} index from PostgreSQL on startup when the index is empty.
 * Controlled by {@code app.post.seed.enabled}. Seeding failures are non-fatal: Elasticsearch is a
 * rebuildable secondary tier, so the application starts even if it is unavailable, and post search
 * degrades to an empty page.
 *
 * <p>Batches walk {@code created_at} with a strict less-than keyset, which can skip equal-timestamp
 * rows across batch boundaries — an accepted tradeoff; reseed by clearing the index.
 */
@Slf4j
@Component
public class PostIndexSeedRunner implements ApplicationRunner {

    private static final int BATCH_SIZE = 100;

    private final PostRepository postRepository;
    private final PostSearchRepository postSearchRepository;
    private final ElasticsearchOperations elasticsearchOperations;
    private final HashtagService hashtagService;
    private final PostMapper postMapper;
    private final PostProperties properties;

    public PostIndexSeedRunner(
            PostRepository postRepository,
            PostSearchRepository postSearchRepository,
            ElasticsearchOperations elasticsearchOperations,
            HashtagService hashtagService,
            PostMapper postMapper,
            PostProperties properties) {
        this.postRepository = postRepository;
        this.postSearchRepository = postSearchRepository;
        this.elasticsearchOperations = elasticsearchOperations;
        this.hashtagService = hashtagService;
        this.postMapper = postMapper;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.getSeed().isEnabled()) {
            return;
        }
        try {
            IndexOperations indexOps = elasticsearchOperations.indexOps(PostDocument.class);
            // Repository auto-creation is disabled (createIndex = false), so create the index with
            // its ngram mapping here once Elasticsearch is confirmed reachable.
            if (!indexOps.exists()) {
                indexOps.createWithMapping();
            }
            long count = elasticsearchOperations.count(Query.findAll(), PostDocument.class);
            // Skip bootstrap if the index already has documents — partial population is also
            // skipped
            if (count > 0) {
                log.info("Posts index already contains {} documents; skipping seed", count);
                return;
            }
            long indexed = 0;
            OffsetDateTime cursor = null;
            while (true) {
                PageRequest page = PageRequest.of(0, BATCH_SIZE);
                List<Post> batch =
                        cursor == null
                                ? postRepository.findFirstPublished(PostStatus.PUBLISHED, page)
                                : postRepository.findPublishedBefore(
                                        PostStatus.PUBLISHED, cursor, page);
                if (batch.isEmpty()) {
                    break;
                }
                List<UUID> ids = batch.stream().map(Post::getId).toList();
                Map<UUID, List<UUID>> hashtagIds = hashtagService.getHashtagIdsForPosts(ids);
                List<PostDocument> documents =
                        batch.stream()
                                .map(
                                        post ->
                                                postMapper.toDocument(
                                                        post,
                                                        hashtagIds
                                                                .getOrDefault(
                                                                        post.getId(), List.of())
                                                                .stream()
                                                                .map(UUID::toString)
                                                                .toList()))
                                .toList();
                postSearchRepository.saveAll(documents);
                indexed += documents.size();
                cursor = batch.get(batch.size() - 1).getCreatedAt();
                if (batch.size() < BATCH_SIZE) {
                    break;
                }
            }
            log.info("Posts index seeded with {} documents", indexed);
        } catch (Exception e) {
            // Non-fatal: never block startup on the rebuildable search tier.
            log.warn("Posts index seeding skipped due to Elasticsearch error: {}", e.getMessage());
        }
    }
}
