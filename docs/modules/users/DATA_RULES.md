# Users Module — Data Rules

**Implementation status**: Scaffolding only. No Service, Controller, or Repository Java files exist for this module beyond the `User` entity and auth-owned repositories.

---

## Section 1: Canonical Data

| Table | Key Columns | Notes |
|-------|-------------|-------|
| `users` | `id`, `username`, `email`, `display_name`, `bio`, `avatar_url`, `website_url`, `is_private`, `is_verified`, `deleted_at` | Profile fields. Shared with auth module (auth owns the row lifecycle; users module manages profile fields). |
| `user_settings` | `user_id` (PK/FK), `notify_*`, `show_activity_status`, `allow_story_replies`, `allow_message_requests` | Per-user notification and privacy preferences. Created alongside the user account. |
| `push_tokens` | `id`, `user_id`, `token`, `platform`, `last_used_at` | Device push notification tokens. Multiple tokens per user (one per device). |

These tables cannot be rebuilt from any other source if lost.

---

## Section 2: Derived Data / Cache / Projection

| Data | Location | Rebuilt From | Rebuild Trigger |
|------|----------|--------------|-----------------|
| `users.follower_count` | `users` table | `COUNT(*)` from `follows` where `following_id = user.id` and `status = 'accepted'` | Trigger `trg_follow_counts` (V16) |
| `users.following_count` | `users` table | `COUNT(*)` from `follows` where `follower_id = user.id` and `status = 'accepted'` | Trigger `trg_follow_counts` (V16) |
| `users.post_count` | `users` table | `COUNT(*)` from `posts` where `user_id = user.id`, `status = 'published'`, `deleted_at IS NULL` | Trigger `trg_post_count` (V16) |
| `users.updated_at` | `users` table | Auto-maintained | Trigger `trg_users_updated_at` (V16) |
| User profile cache | Redis | Rebuild from `users` table | Cache miss or TTL expiry |
| Username search index | GIN index `idx_users_username_trgm` + `idx_users_fts` | Rebuild by reindexing `users` | Automatic on write |

---

## Section 3: Business Rules

### A. Rules Enforced by the Database

| Rule | Enforced By |
|------|-------------|
| `username` max 30 characters, unique, not null | `VARCHAR(30) UNIQUE NOT NULL` |
| `email` max 255 characters, unique, not null | `VARCHAR(255) UNIQUE NOT NULL` |
| `display_name` max 100 characters | `VARCHAR(100)` |
| `is_private` defaults to `FALSE` | `DEFAULT FALSE NOT NULL` |
| `push_tokens.platform` must be one of `'ios'`, `'android'`, `'web'` | `CHECK (platform IN ('ios', 'android', 'web'))` |
| `push_tokens.token` is globally unique | `UNIQUE` constraint |
| Deleting a user cascades to `user_settings` and `push_tokens` | `ON DELETE CASCADE` on FK references |
| Queries must filter `WHERE deleted_at IS NULL` for active users | Soft-delete pattern; enforced by partial indexes |

### B. Rules Enforced by Application Code

| Rule | Service / Component |
|------|---------------------|
| Profile update must not change `email` without re-verification | `[NOT YET IMPLEMENTED]` |
| `username` change must check uniqueness against non-deleted users | `[NOT YET IMPLEMENTED]` |
| `user_settings` row is created with defaults when a new user registers | `[NOT YET IMPLEMENTED]` — should be in `AuthServiceImpl` on registration |
| A push token must be de-registered when the user logs out from a device | `[NOT YET IMPLEMENTED]` |
| Soft delete of a user must set `deleted_at = NOW()`; must not hard-delete | `[NOT YET IMPLEMENTED]` |
| Restoring a soft-deleted user must set `deleted_at = NULL` | `[NOT YET IMPLEMENTED]` |
| Avatar upload uses `media_assets` storage; `users.avatar_url` stores the CDN URL, not a FK | `[NOT YET IMPLEMENTED]` |

**Username and Email Retention on Soft Delete**:
- `UNIQUE` constraints on `username` and `email` are enforced even when `deleted_at IS NOT NULL`.
- Soft-deleted accounts retain their `username` and `email`. These identifiers are not reusable by other accounts.
- See `GLOBAL_RULES.md` — Username and Email Retention Policy for full details.

### C. Scope Simplifications

- `users.avatar_url` is a plain `TEXT` CDN URL, not a foreign key to `media_assets`. This avoids enforcing deletion ordering but means avatar asset and profile are not referentially linked.
- No account deactivation self-service flow yet; `status` changes are admin-only actions.

---

## Section 4: Inter-Module Dependencies

| Dependency | Direction | Nature |
|------------|-----------|--------|
| `auth` | inbound | Auth module owns the `users` row lifecycle (creation, soft delete); users module manages profile fields on the same row |
| `media` | outbound | `avatar_url` is a CDN URL sourced from `media_assets`; relationship is by convention, not FK |
| `notification` | inbound | Notification settings on `user_settings` are read by the notification module before dispatching |
| `social` | inbound | Follow/block state affects profile visibility rules |
