package com.app.modules.hashtag.repository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import com.app.modules.hashtag.entity.Hashtag;
import com.app.modules.hashtag.enums.HashtagStatus;

/**
 * Native-SQL implementation of the administrative hashtag reads.
 *
 * <p>Predicates are appended only when the corresponding filter is present rather than being
 * written as {@code :param IS NULL OR column = :param}. PostgreSQL cannot turn that disjunction
 * into an index condition, so the always-present form costs a sequential scan on every filtered
 * call.
 *
 * <p>The keyset predicate uses a row-value comparison, {@code (created_at, id) < (?, ?)}, which the
 * planner resolves together with the status equality as a single seek on {@code
 * idx_hashtags_status_created}. The expanded {@code a < ? OR (a = ? AND b < ?)} form does not.
 */
public class HashtagRepositoryImpl implements HashtagRepositoryCustom {

    private static final String SELECT_HASHTAG = "SELECT h.* FROM hashtags h";
    private static final String KEYSET_ORDER =
            " ORDER BY h.created_at DESC, h.id DESC LIMIT :limit";
    private static final String KEYSET_PREDICATE =
            "(h.created_at, h.id) < (CAST(:cursorCreatedAt AS timestamptz), CAST(:cursorId AS uuid))";
    private static final String STATUS_PREDICATE = "h.status = CAST(:status AS hashtag_status)";

    private final EntityManager entityManager;

    public HashtagRepositoryImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    // SQL text only joins constant predicates; every caller-controlled value is bound separately.
    @SuppressWarnings("java:S2077")
    public List<Hashtag> findAdminPage(
            HashtagStatus status, OffsetDateTime cursorCreatedAt, UUID cursorId, int limit) {
        List<String> predicates = new ArrayList<>();
        if (status != null) {
            predicates.add(STATUS_PREDICATE);
        }
        boolean paged = cursorCreatedAt != null && cursorId != null;
        if (paged) {
            predicates.add(KEYSET_PREDICATE);
        }

        Query query =
                entityManager.createNativeQuery(
                        SELECT_HASHTAG + where(predicates) + KEYSET_ORDER, Hashtag.class);
        if (status != null) {
            query.setParameter("status", status.toJson());
        }
        bindKeyset(query, paged, cursorCreatedAt, cursorId);
        query.setParameter("limit", limit);
        return typedResult(query);
    }

    @Override
    // SQL text only joins constant predicates; every caller-controlled value is bound separately.
    @SuppressWarnings("java:S2077")
    public List<Hashtag> searchAdmin(
            String queryText,
            HashtagStatus status,
            OffsetDateTime cursorCreatedAt,
            UUID cursorId,
            int limit) {
        List<String> predicates = new ArrayList<>();
        predicates.add("h.name ILIKE :pattern");
        if (status != null) {
            predicates.add(STATUS_PREDICATE);
        }
        boolean paged = cursorCreatedAt != null && cursorId != null;
        if (paged) {
            predicates.add(KEYSET_PREDICATE);
        }

        Query query =
                entityManager.createNativeQuery(
                        SELECT_HASHTAG + where(predicates) + KEYSET_ORDER, Hashtag.class);
        query.setParameter("pattern", "%" + escapeLikeWildcards(queryText) + "%");
        if (status != null) {
            query.setParameter("status", status.toJson());
        }
        bindKeyset(query, paged, cursorCreatedAt, cursorId);
        query.setParameter("limit", limit);
        return typedResult(query);
    }

    private static String where(List<String> predicates) {
        return predicates.isEmpty() ? "" : " WHERE " + String.join(" AND ", predicates);
    }

    private static void bindKeyset(
            Query query, boolean paged, OffsetDateTime cursorCreatedAt, UUID cursorId) {
        if (paged) {
            query.setParameter("cursorCreatedAt", cursorCreatedAt);
            query.setParameter("cursorId", cursorId);
        }
    }

    // An unescaped '%' or '_' in an administrator's search text would silently widen the match to
    // every row rather than looking for the literal character the administrator typed.
    private static String escapeLikeWildcards(String query) {
        return query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    @SuppressWarnings("unchecked")
    private static List<Hashtag> typedResult(Query query) {
        return query.getResultList();
    }
}
