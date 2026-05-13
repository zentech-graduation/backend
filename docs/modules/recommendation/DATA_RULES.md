# Recommendation Module — Data Rules

**Implementation status**: Scaffolding only. No Service, Controller, or Repository Java files exist for this module.

---

## Section 1: Canonical Data

| Table | Key Columns | Notes |
|-------|-------------|-------|
| `categories` | `id`, `name`, `slug`, `parent_id` | Interest taxonomy. Hierarchical (self-referential via `parent_id`). Managed by the team, not by users. |
| `user_interests` | `user_id`, `category_id`, `score` | User-to-category interest weights. Updated by ML jobs or explicit user selection. `score` is a decimal in [0, 10]. If scores are assigned solely by ML jobs, treat as Derived. |
| `post_categories` | `post_id`, `category_id`, `confidence` | Post-to-category assignments. `confidence` in [0, 1]. Assigned at upload or by ML classifier. |
| `user_events` | `id`, `user_id`, `session_id`, `event_type`, `entity_type`, `entity_id`, `metadata`, `created_at` | Raw behavioral event stream. Append-only. Partitioned by month (`PARTITION BY RANGE (created_at)`). |

These tables cannot be rebuilt if lost — `user_events` is the raw behavioral record; `user_interests` may reflect manual user selections not derivable from behavior alone.

---

## Section 2: Derived Data / Cache / Projection

| Data | Location | Rebuilt From | Rebuild Trigger |
|------|----------|--------------|-----------------|
| `post_interaction_scores` | `post_interaction_scores` table | Recomputed from `post_likes`, `comments`, `post_saves`, `user_events` by background scheduler | Scheduled job. Never write to this table from Controller or Service code. |
| `user_similarity` scores | `user_similarity` table | Recomputed by ML job from `user_interests`, `user_events`, `follows` | Scheduled ML job. Constraint `user_id_a < user_id_b` eliminates duplicate pairs. |
| Explore page feed | Redis cache | Built from `post_interaction_scores` ordered by `total_score DESC` | Cache miss or TTL expiry |

---

## Section 3: Business Rules

### A. Rules Enforced by the Database

| Rule | Enforced By |
|------|-------------|
| `categories.name` and `categories.slug` must be unique | `UNIQUE NOT NULL` on each |
| `user_interests` allows at most one row per (user, category) pair | Compound `PRIMARY KEY (user_id, category_id)` |
| `post_categories` allows at most one row per (post, category) pair | Compound `PRIMARY KEY (post_id, category_id)` |
| `post_categories.confidence` must be in [0, 1] | `DECIMAL(4,3)` precision; application must enforce range |
| `user_similarity` must use `(min_id, max_id)` ordering to prevent duplicate pairs | `CHECK (user_id_a < user_id_b)` |
| `user_similarity.similarity_score` must be in [0, 1] | `CHECK (similarity_score BETWEEN 0 AND 1)` |
| `user_events.event_type` must be one of the 20 values in `event_type` enum | `event_type` enum |
| `user_events.platform` must be one of `'ios'`, `'android'`, `'web'` if provided | `CHECK (platform IN ('ios', 'android', 'web'))` |
| Deleting a user cascades to `user_interests`, `user_similarity`, and `user_events` rows | `ON DELETE CASCADE` on all FK references |
| Deleting a post cascades to `post_categories` and `post_interaction_scores` | `ON DELETE CASCADE` on FK references |
| `user_events` is partitioned by `created_at` month; a default partition catches unmatched dates | Declarative partitioning; `user_events_default` partition |

### B. Rules Enforced by Application Code

| Rule | Service / Component |
|------|---------------------|
| `user_events` rows are append-only; existing events must never be updated or deleted | `[NOT YET IMPLEMENTED]` |
| Event writes must be fire-and-forget (non-blocking to the user action that triggered them) | `[NOT YET IMPLEMENTED]` |
| `post_interaction_scores.engagement_score` is computed as: `likes + comments * 2 + saves * 3` | `[NOT YET IMPLEMENTED]` — defined in schema comment |
| `post_interaction_scores.recency_score` applies a time-decay factor based on `posts.created_at` | `[NOT YET IMPLEMENTED]` |
| `user_interests.score` is updated by an ML job; application code must not overwrite ML-derived scores directly | `[NOT YET IMPLEMENTED]` |
| New `user_events` partitions must be created before their `created_at` range begins (monthly) | `[NOT YET IMPLEMENTED]` — production uses `pg_partman` |
| `post_categories.confidence` must be in [0.000, 1.000]; validate before insert | `[NOT YET IMPLEMENTED]` |

### C. Scope Simplifications

- Recommendation scores (`post_interaction_scores`, `user_similarity`) are computed by batch jobs, not in real-time. Feed ranking may lag behind actual user behavior by minutes.
- `user_events` partitions are pre-created only through June 2026; a default partition catches overflow. A partition management job (e.g., `pg_partman`) must be set up before production.
- No A/B testing infrastructure for recommendation algorithms.
- No explicit user "not interested" signal; only positive engagement is captured.

---

## Section 4: Inter-Module Dependencies

| Dependency | Direction | Nature |
|------------|-----------|--------|
| `users` | inbound | `user_interests`, `user_events`, `user_similarity` reference `users.id` |
| `post` | inbound | `post_categories`, `post_interaction_scores` reference `posts.id`; `user_events` references posts via `entity_id` |
| `hashtag` | inbound | `user_events` captures `hashtag_click` events with `entity_type = 'hashtag'` |
| `story` | inbound | `user_events` captures `story_view` events with `entity_type = 'story'` |
