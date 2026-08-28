# Recommendation Module — Data Rules

**Implementation status**: Partially implemented. `user_events` has two writers, a repository and a read path, and the personalized feed is built on top; nothing else in this module does.

Implemented: `RecommendationFeedService` (the Gorse-backed ranked feed, see `README.md` in this folder), `GorseClient`, `RecommendationFeedbackConsumer` and `UserEventJdbcRepository` (durable engagement writes to `user_events` plus Gorse feedback), `UserEventRecorder` (fire-and-forget analytics writes to `user_events`), `ImpressionService` (batched impression ingest), `UserEventRepository` (read, keyset-paged), `UserEventsPartitionJob` (partition maintenance), and the `UserEvent` entity with its enum and converters.
The activity-log read surface lives in the `admin` module; this module owns the table and both write paths.

The two writers exist because their durability contracts are opposites and cannot be met by one component.
`UserEventRecorder` must never fail or slow the request that triggered it, so it drops rows under pressure.
`RecommendationFeedbackConsumer` writes the canonical engagement record that Gorse is rebuilt from, so it must not drop anything and must be idempotent across redelivery.

`ImpressionService` is a producer for the second of those, not a third writer.
It writes no `user_events` row itself; it enqueues one outbox row per impression and the consumer writes the row, so impressions inherit the durable path's guarantees rather than needing their own.

### Impression ingest

An impression is a client-reported observation that a post was at least half visible in the viewport for one continuous second.
The client performs that measurement; the backend owns the endpoint, the transport, and the durability.

Impressions are submitted in batches to a single endpoint under the recommendations root.
The batch is bounded and an oversized batch is rejected as a validation error rather than truncated, so a client is never told signals were accepted that were in fact discarded.

**Idempotency is client-keyed.**
Each impression carries a client-generated `impressionId`, which becomes the outbox `event_id`.
A retry after a network failure therefore resends the same ids and is absorbed by three constraints that already existed: `outbox_events.event_id` is `UNIQUE`, `processed_messages` is unique on `(consumer_name, event_id)`, and `UserEventJdbcRepository.insertIgnoreDuplicate` keys the row on the event id.
`OutboxService.enqueueOnce` is the entry point; the ordinary `enqueue` generates a random event id and would count a resubmitted batch twice.

**`posts.view_count` is never touched by this path**, and neither is it touched by `POST /posts/{postId}/view`.
Both endpoints emit `post.viewed.v1` and neither writes the counter, which a background job maintains and application code never writes.
The two endpoints are deliberately distinct and must not be merged: one records a single deliberate open of one post, the other ingests batched passive viewport impressions carrying dwell and a surface.

Dwell travels as `dwellSeconds` and the surface as `surface`; both are additive payload keys, so a `post.viewed.v1` message enqueued before they existed stays readable and falls back to a unit feedback value.

Not implemented: `categories`, `user_interests`, `post_categories`, `post_interaction_scores`, `user_similarity`.

### What Gorse receives, and what it deliberately does not

| Signal | Gorse feedback type | Value | Note |
|--------|--------------------|-------|------|
| `post.liked.v1` | `like` | 1.0 | positive |
| `post.saved.v1` | `save` | 1.0 | positive |
| `comment.created.v1` | `comment` | 1.0 | positive |
| `post.shared.v1` | `share` | 1.0 | positive |
| `comment.liked.v1` | `like` | 0.5 | attributed to the parent post |
| `post.viewed.v1` | `read` | dwell seconds, or 1.0 | the negative training signal |
| story views | none | none | recorded in `user_events` only |

**A comment like is a deliberate reduction, not the raw signal.**
It is a user-to-comment relation, but Gorse's item space is posts, so the only usable mapping attributes it to the comment's parent post.
It reuses the `like` feedback type at half weight rather than taking a type of its own, because a new type would need its own entry in `positive_feedback_types` and would dilute the bucket the collaborative model trains on.
The raw signal is not lost: `user_events` still records the true `comment_like` event type.

**Feedback `Value` accumulates; it is not overwritten.**
Measured against v0.5.11: inserting 2.0 then 5.0 for one (type, user, item) tuple leaves a single row holding 7.0.
So an impression's dwell is a running total of that viewer's time on that post, not the duration of the last impression, which is the intended reading.
It also means the row-level idempotency of `insertFeedback` does not extend to the value: a replay that reached Gorse twice would inflate it.
What prevents that is the inbox guard keyed on the event id, not the recommender.

**A share is a positive example.**
It costs the user more effort than a like and is a deliberate endorsement to a specific person rather than a passive signal, so `share` is listed in `positive_feedback_types`.

**Story views are never sent to Gorse. Do not reopen this.**
Stories expire after 24 hours, while Gorse fits on a schedule and caches recommendation results for the configured `cache_expire`, so a story could be recommended after it has ceased to exist.
There is also no item-space fit: stories are not posts, and inserting them as items would pollute the catalogue the post recommender ranks over.
Story views are recorded in `user_events` only.

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
| `user_events` rows are append-only; existing events must never be updated or deleted | `UserEventRecorder` and `UserEventJdbcRepository` issue only `INSERT`; the `UserEvent` entity is `@Immutable` and has no persist path |
| An impression must survive a client retry without being counted twice | `ImpressionServiceImpl` via `OutboxService.enqueueOnce` - the client-supplied `impressionId` becomes the outbox `event_id`, and the `UNIQUE` constraint on it absorbs the resubmission. Never use the ordinary `enqueue` on this path; it generates a random event id and would double count |
| An impression must never write `posts.view_count` | `ImpressionServiceImpl` - it only enqueues an outbox row. The counter is maintained by a background job and is never written from application code, on this path or the single-post view path |
| Analytics event writes must be fire-and-forget (non-blocking to the user action that triggered them) | `UserEventRecorder` - the insert runs on a virtual thread of its own, so it neither joins nor extends the caller's transaction, and every failure ends in a warn log and a dropped row |
| An analytics write must never fail the request that triggered it | `UserEventRecorder` - no retry, no outbox, no dead letter. `OutboxService` exists for events that must reach RabbitMQ; these are not those |
| Event writes must not be able to exhaust the connection pool | `UserEventRecorder` - submission is bounded by a permit count well under the Hikari pool size, and a submission with no permit free is dropped immediately rather than queued or blocked, because backpressure onto a request thread would defeat the rule above |
| `UserEventRecorder` writes only three event types | `session_start` on any route that issues a session, `search` on both search surfaces carrying the term, `profile_view` for another account's profile. Chosen for investigative value per unit of write volume |
| Engagement event writes must not drop rows and must survive redelivery | `RecommendationFeedbackConsumer` with `UserEventJdbcRepository.insertIgnoreDuplicate` - the row id is the domain event id and the insert is `ON CONFLICT DO NOTHING`, so a replay is a no-op. These rows are the canonical record Gorse is rebuilt from, which is why they take the durable path rather than the dropping one |
| An engagement write must never fail the user action that triggered it | `PostLikeServiceImpl` / `PostSaveServiceImpl` enqueue an outbox row inside the domain transaction; the event reaches `user_events` and Gorse later, off the request thread |
| A view of one's own profile is not recorded | `UserServiceImpl.assemblePublicProfile` - excluded at the call site rather than filtered out later, so the table does not fill with the views that answer no question |
| A read of `user_events` must be bounded by `created_at` | `AdminUserEventServiceImpl` - the window is the only predicate that prunes partitions; a read bounded only by `user_id` touches every partition ever declared |
| `post_interaction_scores.engagement_score` is computed as: `likes + comments * 2 + saves * 3` | `[NOT YET IMPLEMENTED]` — defined in schema comment |
| `post_interaction_scores.recency_score` applies a time-decay factor based on `posts.created_at` | `[NOT YET IMPLEMENTED]` |
| `user_interests.score` is updated by an ML job; application code must not overwrite ML-derived scores directly | `[NOT YET IMPLEMENTED]` |
| New `user_events` partitions must be created before their `created_at` range begins (monthly) | `UserEventsPartitionJob` - ensures the current month and the next two, daily. It ensures the whole window rather than only its far edge, and runs daily rather than monthly, because a run missed on the first of a month would otherwise leave a hole that outlives the outage |
| `post_categories.confidence` must be in [0.000, 1.000]; validate before insert | `[NOT YET IMPLEMENTED]` |

### C. Scope Simplifications

- Recommendation scores (`post_interaction_scores`, `user_similarity`) are computed by batch jobs, not in real-time. Feed ranking may lag behind actual user behavior by minutes.
- `user_events` partitions are maintained by `UserEventsPartitionJob`, which ensures the current month and the next two, daily; a default partition still catches anything outside that window. The earlier statement that partitions existed only through June 2026 is obsolete.
- No A/B testing infrastructure for recommendation algorithms.
- No explicit user "not interested" signal. Negative training examples are inferred rather than declared: a `read` with no accompanying positive feedback is what the factorization machine ranker trains against. A seeded database with no read signal therefore teaches the ranker nothing, which is why `SeedOutboxEmitter` emits `post.viewed.v1` and `AnalyticsSeedWriter` no longer writes `post_view` rows of its own.

---

## Section 4: Inter-Module Dependencies

| Dependency | Direction | Nature |
|------------|-----------|--------|
| `users` | inbound | `user_interests`, `user_events`, `user_similarity` reference `users.id` |
| `post` | inbound | `post_categories`, `post_interaction_scores` reference `posts.id`; `user_events` references posts via `entity_id` |
| `hashtag` | inbound | `user_events` captures `hashtag_click` events with `entity_type = 'hashtag'` |
| `story` | inbound | `user_events` captures `story_view` events with `entity_type = 'story'` |
