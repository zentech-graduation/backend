# Comment Module — Data Rules

**Implementation status**: Fully implemented.
Threaded comment CRUD (create with HTTP idempotency, edit, soft-delete subtree), likes, content moderation, real-time WebSocket fanout, a Redis recent-comment cache, and notification fanout are all in place, with unit and integration coverage.

---

## Section 1: Canonical Data

| Table | Key Columns | Notes |
|-------|-------------|-------|
| `comments` | `id`, `post_id`, `user_id`, `parent_id`, `root_id`, `depth`, `content`, `moderation_status`, `deleted_at` | Core comment entity. Adjacency list with `root_id` and `depth` for efficient subtree queries. Soft-deleted via `deleted_at`. |
| `comment_likes` | `user_id`, `comment_id`, `created_at` | One row per (user, comment) pair; compound PK prevents duplicate likes. Canonical like signal for comments. |
| `comment_write_idempotency` | `id`, `user_id`, `idempotency_key`, `request_hash`, `response_body`, `created_at` | Caches the first response for a `(user_id, idempotency_key)` pair so a retried create returns the original result instead of a duplicate comment (V27). |

`comments` and `comment_likes` cannot be rebuilt from any other source if lost.
`comment_write_idempotency` is a durable de-duplication ledger, not derived data: its rows cannot be reconstructed, but losing them degrades only retry safety, not comment content.

---

## Section 2: Derived Data / Cache / Projection

| Data | Location | Rebuilt From | Rebuild Trigger |
|------|----------|--------------|-----------------|
| `comments.like_count` | `comments` table | `COUNT(*)` from `comment_likes` where `comment_id = comment.id` | Trigger `trg_comment_like_count` (V16) |
| `comments.reply_count` | `comments` table | `COUNT(*)` from `comments` where `parent_id = comment.id` and `deleted_at IS NULL` | Trigger `trg_comment_reply_count` (V16) |
| `comments.updated_at` | `comments` table | Auto-maintained | Trigger `trg_comments_updated_at` (V16) |
| `posts.comment_count` | `posts` table | `COUNT(*)` from `comments` where `post_id = post.id` and `deleted_at IS NULL` | Trigger `trg_post_comment_count` (V16) |
| Recent-comment page cache | Redis, key prefix `comment:recent:v3:` | Re-read from `comments` | Cache miss or 300-second TTL expiry |

---

## Section 3: Business Rules

### A. Rules Enforced by the Database

| Rule | Enforced By |
|------|-------------|
| `depth` must be between 0 and 10 (inclusive) | `CHECK (depth BETWEEN 0 AND 10)` (V07) |
| `like_count` and `reply_count` are non-negative | `CHECK (column >= 0)` (V07) |
| `comment_likes` allows at most one like per (user, comment) pair | Compound `PRIMARY KEY (user_id, comment_id)` (V07) |
| At most one cached response per user and idempotency key | `UNIQUE (user_id, idempotency_key)` (V27) |
| `moderation_status` defaults to `approved` | `VARCHAR(20) NOT NULL DEFAULT 'approved'` (V26) |
| Deleting a post cascades to all its comments | `ON DELETE CASCADE` on `comments.post_id` |
| Deleting a parent comment cascades to all its child replies | `ON DELETE CASCADE` on `comments.parent_id` and `comments.root_id` |
| Deleting a user cascades to their comments | `ON DELETE CASCADE` on `comments.user_id` |

`moderation_status` is a `VARCHAR`, not a PostgreSQL enum, so adding a state needs no enum migration.
This is a deliberate departure from the enum-as-constraint-layer rule in `GLOBAL_RULES.md`; the tradeoff is that invalid states are not rejected by the database.

### B. Rules Enforced by Application Code

| Rule | Service / Component |
|------|---------------------|
| Top-level comments have `depth = 0`, `parent_id IS NULL`, `root_id IS NULL`. No CHECK constraint enforces this combination. | `CommentServiceImpl.create` |
| `root_id` is set to the top-level ancestor's `id` for replies at any depth | `CommentServiceImpl.create` — `parent.getRootId() != null ? parent.getRootId() : parent.getId()` |
| `depth` is set to `parent.depth + 1` when creating a reply | `CommentServiceImpl.create` |
| A comment whose `depth` would exceed 10 is rejected before the database call | `CommentServiceImpl.create` — throws `COMMENT_DEPTH_EXCEEDED` (400) |
| A user cannot like their own comment | `CommentServiceImpl.like` — throws `COMMENT_FORBIDDEN` (403) |
| Soft delete sets `deleted_at` on the comment **and its whole subtree** in one statement | `CommentRepository.softDeleteSubtree`, called by `CommentServiceImpl.delete` |
| Soft-deleted comments are excluded from every query | `@SQLRestriction("deleted_at IS NULL")` on the `Comment` entity |
| Only the comment owner may edit their comment | `CommentServiceImpl.edit` — throws `COMMENT_FORBIDDEN` (403) |
| Only the comment owner **or an admin** may soft-delete a comment | `CommentServiceImpl.delete` — owner check with an admin override |
| Content is normalized before moderation and persistence | `CommentContentNormalizer` |
| Content failing a blocked-word rule is rejected on create and on edit | `CommentModerationServiceImpl.check` — throws `COMMENT_MODERATION_REJECTED` (422) |
| A repeat create within the slow-mode window is rejected | `CommentServiceImpl` — throws `COMMENT_SLOW_MODE_ACTIVE` (429); disabled when `slowModeSeconds` is 0 |
| An idempotency key reused with a different request payload is rejected | throws `COMMENT_IDEMPOTENCY_CONFLICT` (409) |
| Flat reply lists filter by `root_id`; no `WITH RECURSIVE` CTE is used | `CommentRepository` |
| At most 10 `@mention` usernames are resolved per comment | `CommentServiceImpl` — `MENTION` pattern `@([a-zA-Z0-9_]{1,30})`, `MAX_MENTIONS = 10` |
| Mention usernames resolve only to non soft-deleted users | `CommentUserRepository.findByUsernameAndDeletedAtIsNull` |
| Commenting on a post generates a `comment_post` notification for the post owner | `CommentNotificationConsumer`, from `comment.created.v1` |
| Replying to a comment generates a `reply_comment` notification for the parent comment owner | `CommentNotificationConsumer`, from `comment.created.v1` |
| User mentions in comment `content` generate `mention_comment` notifications | `CommentNotificationConsumer`, from the `mentionedUserIds` event field |
| Liking a comment generates a `like_comment` notification | `CommentNotificationConsumer`, from `comment.liked.v1` |
| A user may only comment on a post that is visible to them | `CommentAccessPolicyServiceImpl.assertCanComment` — delegates to `PostVisibilityService.isVisibleTo`, throws `POST_COMMENTING_RESTRICTED` |
| The viewer's per-row like state is batch-resolved, never per row | `CommentViewerStateServiceImpl` |

Notifications are delivered asynchronously through the transactional outbox, not written inline.
`CommentServiceImpl` enqueues a `DomainEventEnvelope` in the same transaction as the comment write; `CommentNotificationConsumer` creates the notification rows after the broker delivers the event.

### C. Scope Simplifications

- Comment depth is capped at 10.
  The cap is enforced both by the database `CHECK` constraint and at the service layer before the insert, so a rejected reply returns `COMMENT_DEPTH_EXCEEDED` rather than a constraint violation.
- Instagram-style UI shows only two levels (top-level plus direct replies).
  Deeper nesting is stored; rendering depth is a client concern.
- No comment edit history is stored; only the current `content` is retained.
  This differs from the `post` module, which has a `post_edit_history` table (V22).
- Moderation is blocked-word matching only.
  There is no classifier, no review queue, and no appeal path; `moderation_status` exists to support one later without a schema change.
- The Redis recent-comment cache is best-effort.
  A cache failure degrades to a database read; it never fails the request.

---

## Section 4: Inter-Module Dependencies

| Dependency | Direction | Nature |
|------------|-----------|--------|
| `post` | inbound | Every comment belongs to a `post_id`; the `comment_count` trigger updates `posts`; the comment-create gate delegates to `PostVisibilityService` |
| `users` | inbound | Every comment belongs to a `user_id`; `@mention` usernames resolve against `users` |
| `social` | inbound | Block and follow state reach comments through the post visibility policy |
| `notification` | outbound | `comment.created.v1` and `comment.liked.v1` drive `comment_post`, `reply_comment`, `mention_comment`, and `like_comment` notifications |
| `report` | inbound | Reports can target a comment via polymorphic `entity_id` |
| `admin` | inbound | Moderator remove and restore actions target comments and write an `admin_actions` audit row |
