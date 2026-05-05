# Story Module — Source of Truth

**Implementation status**: Scaffolding only. No Service, Controller, or Repository Java files exist for this module.

---

## Section 1: Source-of-Truth Data

| Table | Key Columns | Notes |
|-------|-------------|-------|
| `stories` | `id`, `user_id`, `media_asset_id`, `story_type`, `caption`, `expires_at`, `deleted_at` | Core story entity. 24-hour ephemeral by default (`expires_at = NOW() + INTERVAL '24 hours'`). Soft-deleted via `deleted_at`. |
| `story_views` | `story_id`, `viewer_id`, `viewed_at` | Deduplicated view records — one row per (story, viewer) pair. Canonical viewer list. |

These tables cannot be rebuilt from any other source if lost.

---

## Section 2: Derived / Secondary Data

| Data | Location | Rebuilt From | Rebuild Trigger |
|------|----------|--------------|-----------------|
| `stories.view_count` | `stories` table | `COUNT(*)` from `story_views` where `story_id = story.id` | Trigger `trg_story_view_count` (V16) |
| Active stories view | `active_stories` (DB view, V17) | `stories` where `deleted_at IS NULL` and `expires_at > NOW()` | Query-time |
| Story feed cache | Redis | Rebuild from `stories` joined with `follows` | Cache miss or TTL expiry |

---

## Section 3: Business Rules

### A. Rules Enforced by the Database

| Rule | Enforced By |
|------|-------------|
| `story_type` must be one of `'image'`, `'video'` | `story_type` enum |
| `view_count` is non-negative | `CHECK (view_count >= 0)` |
| `story_views` allows at most one view record per (story, viewer) pair | Compound `PRIMARY KEY (story_id, viewer_id)` |
| `expires_at` defaults to 24 hours after creation | `DEFAULT (NOW() + INTERVAL '24 hours')` |
| `stories.media_asset_id` references an existing media asset | `REFERENCES media_assets(id)` (no cascade) |
| Deleting a user cascades to their stories | `ON DELETE CASCADE` on `stories.user_id` |
| Deleting a story cascades to its `story_views` rows | `ON DELETE CASCADE` on `story_views.story_id` |

### B. Rules Enforced by Application Code

| Rule | Service / Component |
|------|---------------------|
| Stories must not be returned if `expires_at <= NOW()` or `deleted_at IS NOT NULL`; use the `active_stories` view or equivalent filter | `[NOT YET IMPLEMENTED]` |
| A viewer should not see their own story-view count increment (story owner sees the viewer list, not their own view) | `[NOT YET IMPLEMENTED]` |
| Only the story owner may soft-delete their story | `[NOT YET IMPLEMENTED]` |
| Viewing a story inserts into `story_views`; duplicate inserts (same viewer) must be ignored (`INSERT ... ON CONFLICT DO NOTHING`) | `[NOT YET IMPLEMENTED]` |
| Stories from private accounts are only visible to accepted followers | `[NOT YET IMPLEMENTED]` |
| Stories from blocked accounts must be excluded from the viewer's feed | `[NOT YET IMPLEMENTED]` |
| Viewing a story generates a `story_view` notification for the story owner | `[NOT YET IMPLEMENTED]` |
| A background cleanup job must hard-delete expired stories (`expires_at < NOW()` and `deleted_at IS NOT NULL`) to reclaim storage | `[NOT YET IMPLEMENTED]` |
| `user_settings.allow_story_replies` governs whether viewers can reply to a story | `[NOT YET IMPLEMENTED]` |

### C. Scope Simplifications

- Expired stories are not automatically removed from the database; a scheduled cleanup job is required. Until the job runs, `expires_at` must be checked on every read.
- No story highlights (saving stories beyond 24 hours).
- No story polls, questions, or interactive stickers.
- Story replies are direct messages (`message_type = 'story_share'`); no dedicated reply table.

---

## Section 4: Inter-Module Dependencies

| Dependency | Direction | Nature |
|------------|-----------|--------|
| `users` | inbound | Every story belongs to a `user_id`; viewer identity tracked via `story_views.viewer_id` |
| `media` | outbound | Each story references exactly one `media_asset_id` |
| `social` | inbound | Follow/block state governs story visibility |
| `notification` | outbound | Story view events trigger `story_view` notification for the story owner |
| `message` | inbound | Stories can be shared into conversations via `messages.shared_story_id` |
| `report` | inbound | Reports can target a story via polymorphic `entity_id` |
