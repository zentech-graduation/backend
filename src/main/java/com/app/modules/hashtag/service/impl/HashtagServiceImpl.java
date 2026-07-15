package com.app.modules.hashtag.service.impl;

import static com.app.modules.hashtag.messaging.HashtagEventTypes.HASHTAG_INDEX_DELETE_V1;
import static com.app.modules.hashtag.messaging.HashtagEventTypes.HASHTAG_INDEX_UPSERT_V1;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.persistence.EntityManager;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.app.common.outbox.service.OutboxService;
import com.app.modules.hashtag.entity.Hashtag;
import com.app.modules.hashtag.entity.PostHashtag;
import com.app.modules.hashtag.entity.PostHashtagId;
import com.app.modules.hashtag.repository.HashtagIndexProjection;
import com.app.modules.hashtag.repository.HashtagRepository;
import com.app.modules.hashtag.repository.PostHashtagRepository;
import com.app.modules.hashtag.service.HashtagService;

@Service
public class HashtagServiceImpl implements HashtagService {

    private static final String AGGREGATE_TYPE_HASHTAG = "hashtag";

    // hashtags.name is VARCHAR(100); names beyond this bound would fail the insert at the DB.
    private static final int MAX_NAME_LENGTH = 100;

    private final HashtagRepository hashtagRepository;
    private final PostHashtagRepository postHashtagRepository;
    private final EntityManager entityManager;
    private final OutboxService outboxService;

    public HashtagServiceImpl(
            HashtagRepository hashtagRepository,
            PostHashtagRepository postHashtagRepository,
            EntityManager entityManager,
            OutboxService outboxService) {
        this.hashtagRepository = hashtagRepository;
        this.postHashtagRepository = postHashtagRepository;
        this.entityManager = entityManager;
        this.outboxService = outboxService;
    }

    @Override
    public String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.strip();
        String withoutHash = trimmed.replaceFirst("^#+", "");
        // Locale.ROOT lowercasing keeps the unique-index key stable across server locales.
        String normalized = withoutHash.strip().toLowerCase(Locale.ROOT);
        // codePointCount, not length(), matches the VARCHAR(100) character limit: a
        // supplementary-plane letter (e.g. CJK Extension B) occupies two UTF-16 code units in a
        // Java String but counts as one Postgres character, so length() would drop valid values
        // early. Over-long tags are dropped rather than truncated so a truncated prefix never
        // aliases a legitimately distinct shorter tag.
        int codePointCount = normalized.codePointCount(0, normalized.length());
        return codePointCount > MAX_NAME_LENGTH ? "" : normalized;
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

        LinkedHashSet<UUID> affectedIds = new LinkedHashSet<>();
        for (String name : names) {
            hashtagRepository.upsertByName(name);
            Hashtag hashtag = hashtagRepository.findByName(name).orElseThrow();
            postHashtagRepository.save(
                    PostHashtag.builder().id(new PostHashtagId(postId, hashtag.getId())).build());
            affectedIds.add(hashtag.getId());
        }

        for (UUID id : affectedIds) {
            enqueueUpsert(id);
        }
    }

    @Override
    @Transactional
    public void removeHashtagsForPost(UUID postId) {
        List<UUID> affected = postHashtagRepository.findHashtagIdsByPostId(postId);
        postHashtagRepository.deleteAllByPostId(postId);

        // Flush so the decremented post_count is readable by the projection query below.
        entityManager.flush();
        Map<UUID, HashtagIndexProjection> projections =
                hashtagRepository.findIndexProjectionsByIdIn(affected).stream()
                        .collect(
                                Collectors.toMap(
                                        HashtagIndexProjection::getId, Function.identity()));

        for (UUID id : affected) {
            HashtagIndexProjection projection = projections.get(id);
            if (projection != null && projection.getPostCount() > 0) {
                enqueueUpsert(id);
            } else {
                outboxService.enqueue(
                        HASHTAG_INDEX_DELETE_V1,
                        HASHTAG_INDEX_DELETE_V1,
                        AGGREGATE_TYPE_HASHTAG,
                        id,
                        null,
                        Map.of("hashtagId", id.toString()));
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, List<UUID>> getHashtagIdsForPosts(Collection<UUID> postIds) {
        if (postIds.isEmpty()) {
            return Map.of();
        }
        return postHashtagRepository.findAllByIdPostIdIn(postIds).stream()
                .collect(
                        Collectors.groupingBy(
                                ph -> ph.getId().getPostId(),
                                Collectors.mapping(
                                        ph -> ph.getId().getHashtagId(), Collectors.toList())));
    }

    // Carry only the hashtag id; the consumer reads name/post_count/created_at from the
    // source-of-truth database. This keeps user free-text out of the event payload (the outbox
    // rejects sensitive-looking values) and lets the post_count gate run against current state.
    private void enqueueUpsert(UUID id) {
        outboxService.enqueue(
                HASHTAG_INDEX_UPSERT_V1,
                HASHTAG_INDEX_UPSERT_V1,
                AGGREGATE_TYPE_HASHTAG,
                id,
                null,
                Map.of("hashtagId", id.toString()));
    }
}
