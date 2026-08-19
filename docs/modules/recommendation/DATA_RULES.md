# Recommendation Module — Data Rules

**Implementation status**: Partially implemented. `user_events` has a writer, a repository and a read path; nothing else in this module does.

Implemented: `UserEventRecorder` (the single writer to `user_events`), `UserEventRepository` (read, keyset-paged), `UserEventsPartitionJob` (partition maintenance), and the `UserEvent` entity with its enum and converters.
The read surface lives in the `admin` module as the administrative activity log; this module owns the table and the write path.

Not implemented: `categories`, `user_interests`, `post_categories`, `post_interaction_scores`, `user_similarity`. No Controller exists in this module.

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

**The catch-all partition is not the safety net it appears to be, and the consequence is measured rather than argued.**

A write whose month has no declared partition does **not** fail. It lands in `user_events_default` silently, so nothing signals the problem.
Once it has, that month's partition can never be created: PostgreSQL refuses with `updated partition constraint for default partition "user_events_default" would be violated by some row`.
The gap becomes permanent, and the trapped rows become unprunable, because every window-bounded read then has to scan the default partition to prove it holds nothing relevant.
Detaching the default and writing to an undeclared month fails outright with `no partition of relation "user_events" found for row`, which is what the default is preventing.

So the default stays, because rejecting an analytics write is worse than absorbing it, and rows accumulating in `user_events_default` are the alert condition. V69 closed the 2026-07 hole that V14 and V28 left between them, and `UserEventsPartitionJob` keeps the horizon ahead from there.

Partition pruning, measured against a table populated across four months:

| Window | Partitions scanned |
|--------|--------------------|
| Entirely inside one month | that month alone |
| Crossing one month boundary | exactly the two months it spans |
| Reaching past the last declared partition | the declared months it spans, plus `user_events_default` |
| Unbounded, `user_id` only | every declared partition and the catch-all |

The last row is the read the activity log's mandatory window exists to make impossible.

### B. Rules Enforced by Application Code

| Rule | Service / Component |
|------|---------------------|
| `user_events` rows are append-only; existing events must never be updated or deleted | `UserEventRecorder` issues only `INSERT`; the `UserEvent` entity is `@Immutable` and has no persist path |
| Event writes must be fire-and-forget (non-blocking to the user action that triggered them) | `UserEventRecorder` - the insert runs on a virtual thread of its own, so it neither joins nor extends the caller's transaction, and every failure ends in a warn log and a dropped row |
| An analytics write must never fail the request that triggered it | `UserEventRecorder` - no retry, no outbox, no dead letter. `OutboxService` exists for events that must reach RabbitMQ; these are not those |
| Event writes must not be able to exhaust the connection pool | `UserEventRecorder` - submission is bounded by a permit count well under the Hikari pool size, and a submission with no permit free is dropped immediately rather than queued or blocked, because backpressure onto a request thread would defeat the rule above |
| Only three event types are ever written | `UserEventRecorder` - `session_start` on any route that issues a session, `search` on both search surfaces carrying the term, `profile_view` for another account's profile. Chosen for investigative value per unit of write volume; `post_view` is an order of magnitude larger than all three together and belongs to a later cycle |
| A view of one's own profile is not recorded | `UserServiceImpl.assemblePublicProfile` - excluded at the call site rather than filtered out later, so the table does not fill with the views that answer no question |
| A read of `user_events` must be bounded by `created_at` | `AdminUserEventServiceImpl` - the window is the only predicate that prunes partitions; a read bounded only by `user_id` touches every partition ever declared |
| `post_interaction_scores.engagement_score` is computed as: `likes + comments * 2 + saves * 3` | `[NOT YET IMPLEMENTED]` — defined in schema comment |
| `post_interaction_scores.recency_score` applies a time-decay factor based on `posts.created_at` | `[NOT YET IMPLEMENTED]` |
| `user_interests.score` is updated by an ML job; application code must not overwrite ML-derived scores directly | `[NOT YET IMPLEMENTED]` |
| New `user_events` partitions must be created before their `created_at` range begins (monthly) | `UserEventsPartitionJob` - ensures the current month and the next two, daily. It ensures the whole window rather than only its far edge, and runs daily rather than monthly, because a run missed on the first of a month would otherwise leave a hole that outlives the outage |
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
