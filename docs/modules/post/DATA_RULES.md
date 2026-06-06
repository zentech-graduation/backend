# Post Module — Data Rules

**Implementation status**: Phase A implementation in progress — core CRUD, lifecycle, likes, saves, visibility, Elasticsearch search, and caption edit history. Outbox events and post-document dual-writes are deferred to Phase B.

---

## Section 1: Canonical Data

| Table | Key Columns | Notes |
|-------|-------------|-------|
| `posts` | `id`, `user_id`, `caption`, `post_type`, `status`, `location_name`, `latitude`, `longitude`, `deleted_at` | Core post entity. Soft-deleted via `deleted_at`. Status lifecycle: `draft → published → archived / removed`. |
| `post_media` | `id`, `post_id`, `media_asset_id`, `position`, `alt_text` | Ordered media items attached to a post. Supports carousel (multiple images/videos). |
| `post_user_tags` | `post_id`, `tagged_user_id`, `media_asset_id`, `x_position`, `y_position` | Users tagged within a post image, with optional pixel-percentage coordinates. |
| `post_likes` | `user_id`, `post_id`, `created_at` | One row per (user, post) pair; compound PK prevents duplicate likes. Canonical like signal. |
| `post_saves` | `user_id`, `post_id`, `created_at` | One row per (user, post) pair; compound PK prevents duplicate saves. Canonical bookmark signal. |
| `post_edit_history` | `id`, `post_id`, `editor_id`, `previous_caption`, `edited_at` | Append-only caption edit audit (V22). Rows are never updated or soft-deleted; retention is permanent until post hard-delete (FK cascade). |

These tables cannot be rebuilt from any other source if lost.

---

## Section 2: Derived Data / Cache / Projection

| Data | Location | Rebuilt From | Rebuild Trigger |
|------|----------|--------------|-----------------|
| `posts.like_count` | `posts` table | `COUNT(*)` from `post_likes` where `post_id = post.id` | Trigger `trg_post_like_count` (V16) |
| `posts.comment_count` | `posts` table | `COUNT(*)` from `comments` where `post_id = post.id` and `deleted_at IS NULL` | Trigger `trg_post_comment_count` (V16) |
| `posts.save_count` | `posts` table | `COUNT(*)` from `post_saves` where `post_id = post.id` | Trigger `trg_post_save_count` (V16) |
| `posts.view_count` | `posts` table | No trigger — updated by background job | Background job; may lag real-time activity |
| `posts.updated_at` | `posts` table | Auto-maintained | Trigger `trg_posts_updated_at` (V16) |
| `users.post_count` | `users` table | `COUNT(*)` from `posts` where `user_id` matches, `status='published'`, `deleted_at IS NULL` | Trigger `trg_post_count` (V16) |
| Post feed cache | Redis | Rebuild from `posts` ordered by `created_at DESC` | Cache miss or TTL expiry |
| `post_interaction_scores` | `post_interaction_scores` table | Computed from `post_likes`, `comments`, `post_saves`, `user_events` by background scheduler | Scheduled background job |

---

## Section 3: Business Rules

### A. Rules Enforced by the Database

| Rule | Enforced By |
|------|-------------|
| `post_type` must be one of `'image'`, `'video'`, `'carousel'` | `post_type` enum |
| `status` must be one of `'draft'`, `'published'`, `'archived'`, `'removed'`; defaults to `'published'` | `post_status` enum, `DEFAULT 'published'` |
| `like_count`, `comment_count`, `save_count`, `view_count` are non-negative | `CHECK (column >= 0)` |
| `post_media.position` must be unique within a post (no two media items at same carousel position) | `UNIQUE (post_id, position)` |
| `post_likes` allows at most one like per (user, post) pair | Compound `PRIMARY KEY (user_id, post_id)` |
| `post_saves` allows at most one save per (user, post) pair | Compound `PRIMARY KEY (user_id, post_id)` |
| `post_user_tags` allows at most one tag per (post, user) pair | Compound `PRIMARY KEY (post_id, tagged_user_id)` |
| Deleting a post cascades to `post_media`, `post_likes`, `post_saves`, `post_user_tags`, `comments` | `ON DELETE CASCADE` on all FK references |
| `post_media` references `media_assets`; media asset must exist | `REFERENCES media_assets(id)` (no cascade — asset deletion is independent) |

### B. Rules Enforced by Application Code

| Rule | Service / Component |
|------|---------------------|
| A `carousel` post must have more than one `post_media` row | `[NOT YET IMPLEMENTED]` |
| Self-like is permitted. There is no constraint preventing a user from liking their own post. | No constraint in schema |
| Only the post owner may update or soft-delete their post | `[NOT YET IMPLEMENTED]` |
| A soft-deleted post must set `deleted_at = NOW()` and `status = 'removed'`; do not hard-delete | `[NOT YET IMPLEMENTED]` |
| `status = 'removed'` by admin sets `deleted_at = NOW()` via admin action | `[NOT YET IMPLEMENTED]` |
| Posts from blocked users must be excluded from feeds | `[NOT YET IMPLEMENTED]` |
| Posts from private accounts are only visible to accepted followers | `[NOT YET IMPLEMENTED]` |
| `posts.view_count` is updated by a background job, not a trigger. It may lag real-time activity. See `GLOBAL_RULES.md` — Counter Policy Exception. | Background job `[NOT YET IMPLEMENTED]` |
| Hashtags in `caption` are parsed and written to `post_hashtags` at publish time | `[NOT YET IMPLEMENTED]` |
| User mentions in `caption` generate `mention_post` notifications | `[NOT YET IMPLEMENTED]` |
| Every caption update appends one `post_edit_history` row recording the pre-edit caption and the editor | `PostServiceImpl` |
| Edit history is readable by the post owner only | `PostServiceImpl` |

### C. Scope Simplifications

- `posts.view_count` is a denormalized counter updated by a **background job**, not a trigger. This diverges from the trigger pattern used for other counters and means `view_count` may lag behind real-time activity.
- No post scheduling (publish at a future time); `status = 'draft'` is the only unpublished state.
- Location data (`latitude`, `longitude`) is stored but no geospatial query support is implemented.

---

## Section 4: Inter-Module Dependencies

| Dependency | Direction | Nature |
|------------|-----------|--------|
| `users` | inbound | Every post belongs to a `user_id`; author identity comes from `users` |
| `media` | outbound | `post_media` references `media_assets` for each attached media item |
| `hashtag` | outbound | Hashtags extracted from `caption` are written to `post_hashtags` + `hashtags` |
| `comment` | inbound | Comments reference `posts.id`; `comment_count` trigger fires on comment table |
| `social` | inbound | Follow/block state governs post visibility; no direct FK dependency |
| `notification` | outbound | Post publish, like, and mention events trigger notification creation |
| `recommendation` | inbound | `post_categories` and `post_interaction_scores` reference `posts.id` |
| `report` | inbound | Reports can target a post via polymorphic `entity_id` |
| `message` | inbound | Messages can share a post via `shared_post_id` (SET NULL on post delete) |
