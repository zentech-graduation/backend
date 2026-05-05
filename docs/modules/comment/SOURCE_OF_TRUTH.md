# Comment Module — Source of Truth

**Implementation status**: Scaffolding only. No Service, Controller, or Repository Java files exist for this module.

---

## Section 1: Source-of-Truth Data

| Table | Key Columns | Notes |
|-------|-------------|-------|
| `comments` | `id`, `post_id`, `user_id`, `parent_id`, `root_id`, `depth`, `content`, `deleted_at` | Core comment entity. Adjacency list with `root_id` and `depth` for efficient subtree queries. Soft-deleted via `deleted_at`. |
| `comment_likes` | `user_id`, `comment_id`, `created_at` | One row per (user, comment) pair; compound PK prevents duplicate likes. Canonical like signal for comments. |

These tables cannot be rebuilt from any other source if lost.

---

## Section 2: Derived / Secondary Data

| Data | Location | Rebuilt From | Rebuild Trigger |
|------|----------|--------------|-----------------|
| `comments.like_count` | `comments` table | `COUNT(*)` from `comment_likes` where `comment_id = comment.id` | Trigger `trg_comment_like_count` (V16) |
| `comments.reply_count` | `comments` table | `COUNT(*)` from `comments` where `parent_id = comment.id` and `deleted_at IS NULL` | Trigger `trg_comment_reply_count` (V16) |
| `comments.updated_at` | `comments` table | Auto-maintained | Trigger `trg_comments_updated_at` (V16) |
| `posts.comment_count` | `posts` table | `COUNT(*)` from `comments` where `post_id = post.id` and `deleted_at IS NULL` | Trigger `trg_post_comment_count` (V16) |

---

## Section 3: Business Rules

### A. Rules Enforced by the Database

| Rule | Enforced By |
|------|-------------|
| `depth` must be between 0 and 10 (inclusive) | `CHECK (depth BETWEEN 0 AND 10)` |
| Top-level comments have `depth = 0`, `parent_id IS NULL`, `root_id IS NULL` | Column semantics; application must set correctly |
| `like_count` and `reply_count` are non-negative | `CHECK (column >= 0)` |
| `comment_likes` allows at most one like per (user, comment) pair | Compound `PRIMARY KEY (user_id, comment_id)` |
| Deleting a post cascades to all its comments | `ON DELETE CASCADE` on `comments.post_id` |
| Deleting a parent comment cascades to all its child replies | `ON DELETE CASCADE` on `comments.parent_id` and `comments.root_id` |
| Deleting a user cascades to their comments | `ON DELETE CASCADE` on `comments.user_id` |

### B. Rules Enforced by Application Code

| Rule | Service / Component |
|------|---------------------|
| `root_id` must be set to the top-level comment's `id` for all replies at any depth | `[NOT YET IMPLEMENTED]` |
| `depth` must be set to `parent.depth + 1` when creating a reply | `[NOT YET IMPLEMENTED]` |
| A comment cannot be created if `depth` would exceed 10 (reject at service layer) | `[NOT YET IMPLEMENTED]` |
| A user cannot like their own comment | `[NOT YET IMPLEMENTED]` |
| Soft delete sets `deleted_at = NOW()` on the comment; descendant replies are cascaded via DB | `[NOT YET IMPLEMENTED]` |
| Soft-deleted comments must not be returned in public API responses | `[NOT YET IMPLEMENTED]` |
| Only the comment owner may edit or soft-delete their comment | `[NOT YET IMPLEMENTED]` |
| Subtree retrieval uses `WITH RECURSIVE` CTE on `parent_id` or filters by `root_id` for flat reply lists | `[NOT YET IMPLEMENTED]` |
| Commenting on a post generates a `comment_post` notification for the post owner | `[NOT YET IMPLEMENTED]` |
| Replying to a comment generates a `reply_comment` notification for the parent comment owner | `[NOT YET IMPLEMENTED]` |
| User mentions in comment `content` generate `mention_comment` notifications | `[NOT YET IMPLEMENTED]` |

### C. Scope Simplifications

- Comment tree is capped at depth 10 to prevent runaway nesting. This is enforced by the DB `CHECK` constraint but must also be caught at the service layer before the DB call.
- Instagram-style UI shows only 2 levels (top-level + direct replies). Deeper nesting is stored but UI rendering is a client concern.
- No comment editing history is stored; only the current `content` is retained.

---

## Section 4: Inter-Module Dependencies

| Dependency | Direction | Nature |
|------------|-----------|--------|
| `post` | inbound | Every comment belongs to a `post_id`; `comment_count` trigger updates `posts` |
| `users` | inbound | Every comment belongs to a `user_id` |
| `notification` | outbound | Comment and reply events trigger notification creation |
| `report` | inbound | Reports can target a comment via polymorphic `entity_id` |
