package com.app.modules.recommendation.repository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import com.app.modules.recommendation.entity.UserEvent;
import com.app.modules.recommendation.enums.UserEventType;

public class UserEventRepositoryImpl implements UserEventRepositoryCustom {

    private final EntityManager entityManager;

    public UserEventRepositoryImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public List<UserEvent> findPage(
            UUID userId,
            OffsetDateTime from,
            OffsetDateTime to,
            UserEventType eventType,
            OffsetDateTime cursorCreatedAt,
            UUID cursorId,
            int limit) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<UserEvent> query = builder.createQuery(UserEvent.class);
        Root<UserEvent> event = query.from(UserEvent.class);
        List<Predicate> predicates = new ArrayList<>();
        // The window is always present, never conditional. It is the only predicate that prunes
        // partitions, so making it optional would turn a bounded read into a scan of the whole
        // history of the table.
        predicates.add(builder.greaterThanOrEqualTo(event.get("createdAt"), from));
        predicates.add(builder.lessThan(event.get("createdAt"), to));
        if (userId != null) {
            predicates.add(builder.equal(event.get("userId"), userId));
        }
        if (eventType != null) {
            predicates.add(builder.equal(event.get("eventType"), eventType));
        }
        if (cursorCreatedAt != null && cursorId != null) {
            predicates.add(
                    builder.or(
                            builder.lessThan(event.get("createdAt"), cursorCreatedAt),
                            builder.and(
                                    builder.equal(event.get("createdAt"), cursorCreatedAt),
                                    builder.lessThan(event.get("id"), cursorId))));
        }
        query.select(event)
                .where(predicates.toArray(Predicate[]::new))
                .orderBy(builder.desc(event.get("createdAt")), builder.desc(event.get("id")));
        return entityManager.createQuery(query).setMaxResults(limit).getResultList();
    }

    @Override
    public List<UUID> findRecentEntityIds(
            UUID userId,
            UserEventType eventType,
            OffsetDateTime from,
            OffsetDateTime to,
            int limit) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<UUID> query = builder.createQuery(UUID.class);
        Root<UserEvent> event = query.from(UserEvent.class);
        // Predicate order mirrors findPage: user_id equality plus the created_at range is exactly
        // what idx_user_events_user (user_id, created_at DESC) serves, so no new index is needed.
        query.select(event.get("entityId"))
                .where(
                        builder.equal(event.get("userId"), userId),
                        builder.equal(event.get("eventType"), eventType),
                        builder.greaterThanOrEqualTo(event.get("createdAt"), from),
                        builder.lessThan(event.get("createdAt"), to))
                .orderBy(builder.desc(event.get("createdAt")));
        return entityManager.createQuery(query).setMaxResults(limit).getResultList();
    }
}
