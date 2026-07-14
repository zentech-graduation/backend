package com.app.modules.admin.repository;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import com.app.modules.admin.entity.AdminAction;
import com.app.modules.admin.enums.AdminActionType;

public class AdminActionRepositoryImpl implements AdminActionRepositoryCustom {

    private final EntityManager entityManager;

    public AdminActionRepositoryImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public AdminAction insert(AdminAction action) {
        entityManager.persist(action);
        return action;
    }

    @Override
    public List<AdminAction> findActions(
            UUID adminId,
            UUID targetUserId,
            AdminActionType actionType,
            OffsetDateTime cursorCreatedAt,
            UUID cursorId,
            int limit) {
        CriteriaBuilder builder = entityManager.getCriteriaBuilder();
        CriteriaQuery<AdminAction> query = builder.createQuery(AdminAction.class);
        Root<AdminAction> action = query.from(AdminAction.class);
        List<Predicate> predicates = new ArrayList<>();
        if (adminId != null) {
            predicates.add(builder.equal(action.get("adminId"), adminId));
        }
        if (targetUserId != null) {
            predicates.add(builder.equal(action.get("targetUserId"), targetUserId));
        }
        if (actionType != null) {
            predicates.add(builder.equal(action.get("actionType"), actionType));
        }
        if (cursorCreatedAt != null && cursorId != null) {
            predicates.add(
                    builder.or(
                            builder.lessThan(action.get("createdAt"), cursorCreatedAt),
                            builder.and(
                                    builder.equal(action.get("createdAt"), cursorCreatedAt),
                                    builder.lessThan(action.get("id"), cursorId))));
        }
        query.select(action)
                .where(predicates.toArray(Predicate[]::new))
                .orderBy(builder.desc(action.get("createdAt")), builder.desc(action.get("id")));
        return entityManager.createQuery(query).setMaxResults(limit).getResultList();
    }
}
