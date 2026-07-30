# Users Module — Data Rules

**Implementation status**: Implemented for profile and settings.
`UserController`, `UserServiceImpl`, `UserSummaryServiceImpl`, `UserRepository`, and `UserSettingsRepository` all exist.
The `push_tokens` table has no Java code at all — no entity, no repository, no service — and account soft delete and restore are likewise unimplemented.

---

## Section 1: Canonical Data

| Table | Key Columns | Notes |
|-------|-------------|-------|
| `users` | `id`, `username`, `email`, `display_name`, `bio`, `avatar_url`, `website_url`, `is_private`, `is_verified`, `status`, `role`, `deleted_at` | Profile fields. Shared with auth module (auth owns the row lifecycle; users module manages profile fields). |
| `user_settings` | `user_id` (PK/FK), `notify_*`, `show_activity_status`, `allow_story_replies`, `allow_message_requests` | Per-user notification and privacy preferences. Created alongside the user account. |
| `push_tokens` | `id`, `user_id`, `token`, `platform`, `last_used_at` | Device push notification tokens. Multiple tokens per user (one per device). **Schema only — no application code reads or writes this table.** |

These tables cannot be rebuilt from any other source if lost.

---

## Section 2: Derived Data / Cache / Projection

| Data | Location | Rebuilt From | Rebuild Trigger |
|------|----------|--------------|-----------------|
| `users.follower_count` | `users` table | `COUNT(*)` from `follows` where `following_id = user.id` and `status = 'accepted'` | Trigger `trg_follow_counts` (V16) |
| `users.following_count` | `users` table | `COUNT(*)` from `follows` where `follower_id = user.id` and `status = 'accepted'` | Trigger `trg_follow_counts` (V16) |
| `users.post_count` | `users` table | `COUNT(*)` from `posts` where `user_id = user.id`, `status = 'published'`, `deleted_at IS NULL` | Trigger `trg_post_count` (V16) |
| `users.updated_at` | `users` table | Auto-maintained | Trigger `trg_users_updated_at` (V16) |
| Username search index | GIN index `idx_users_username_trgm` (V15) | Rebuild by reindexing `users` | Automatic on write |
| Username + display-name FTS index | GIN index `idx_users_fts` (V15) | Rebuild by reindexing `users` | Automatic on write — **currently unused by any query** |

There is no Redis cache of user profiles.
No `@Cacheable` annotation and no `users:*` Redis key exists anywhere in the tree; profile reads always hit PostgreSQL.

---

## Section 3: Business Rules

### A. Rules Enforced by the Database

| Rule | Enforced By |
|------|-------------|
| `username` max 30 characters, unique, not null | `VARCHAR(30) UNIQUE NOT NULL` (V02) |
| `username` uniqueness is **case-sensitive** | Plain `UNIQUE` on the raw column — `Alice` and `alice` are two distinct legal accounts |
| `email` max 255 characters, unique, not null | `VARCHAR(255) UNIQUE NOT NULL` (V02) |
| `display_name` max 100 characters | `VARCHAR(100)` (V02) |
| `is_private` defaults to `FALSE` | `DEFAULT FALSE NOT NULL` (V02) |
| Counter columns are non-negative | `CHECK (column >= 0)` (V02) |
| `push_tokens.platform` must be one of `'ios'`, `'android'`, `'web'` | `CHECK (platform IN ('ios', 'android', 'web'))` (V03) |
| `push_tokens.token` is globally unique | `UNIQUE` constraint (V03) |
| Deleting a user cascades to `user_settings` and `push_tokens` | `ON DELETE CASCADE` on FK references |
| Queries must filter `WHERE deleted_at IS NULL` for active users | Soft-delete pattern; partial index `idx_users_username` carries the predicate |

Note that `idx_users_username_trgm` and `idx_users_fts` carry **no** `deleted_at IS NULL` predicate, unlike `idx_users_username`.
Queries using them must filter soft-deleted rows themselves.

### B. Rules Enforced by Application Code

| Rule | Service / Component |
|------|---------------------|
| Profile update cannot change `email` | Enforced by construction: `UpdateProfileRequest` has no `email` field and is annotated `@JsonIgnoreProperties(ignoreUnknown = false)`, so sending one is rejected |
| `username` change checks uniqueness **table-wide, including soft-deleted rows** | `UserServiceImpl.updateMyProfile` — `userRepository.existsByUsername`, throws `USER_USERNAME_ALREADY_EXISTS` (409) |
| `username` must match `^[a-zA-Z0-9_.]+$` and be 3–30 characters | `UpdateProfileRequest` bean validation |
| An empty string clears `bio`, `avatarUrl`, `websiteUrl`; `null` leaves the field untouched | `UserServiceImpl.updateMyProfile` |
| A block in either direction hides the target profile entirely | `UserServiceImpl.getUserProfile` — returns `NOT_FOUND` so a blocked caller cannot confirm the account exists |
| Social counts are relationship-gated | `UserServiceImpl.getUserProfile` — owner always sees them; a private account reveals them only to accepted followers; a public account reveals them to any authenticated caller; everyone else receives `null` |
| Every user-referencing response carries the viewer's relationship state | `UserServiceImpl.getUserProfile` via `SocialService.loadRelationships` |
| A soft-deleted or unknown user id resolves to a placeholder, not an error | `UserSummaryServiceImpl.loadSummaries` — `displayName` becomes `"Deleted user"`, `username` becomes `null` |
| Batch user lookups are a single query regardless of id count | `UserRepository.findSummariesByIdIn` |
| `user_settings` is created with defaults when a new user registers | `AuthServiceImpl.register` and `CustomOidcUserService` (OAuth path) |
| `status` transitions are admin-only and follow a fixed state machine | `AdminServiceImpl` — ban, unban, suspend, unsuspend; each writes an `admin_actions` audit row in the same transaction |

**Username and Email Retention on Soft Delete**:

- `UNIQUE` constraints on `username` and `email` are enforced even when `deleted_at IS NOT NULL`.
- Soft-deleted accounts retain their `username` and `email`. These identifiers are not reusable by other accounts.
- See `GLOBAL_RULES.md` — Username and Email Retention on Soft Delete for full details.

### C. Not Implemented

These rules are stated in the schema's intent but have no application code behind them.

| Rule | Status |
|------|--------|
| A push token must be de-registered when the user logs out from a device | Not implemented — the `push_tokens` table has no entity, repository, or service |
| Soft delete of a user must set `deleted_at = NOW()` and must not hard-delete | Not implemented — no code path sets `users.deleted_at`; reads filter on it, but nothing writes it |
| Restoring a soft-deleted user must set `deleted_at = NULL` | Not implemented |

### D. Scope Simplifications

- `users.avatar_url` is a plain `TEXT` CDN URL, not a foreign key to `media_assets`.
  This avoids enforcing deletion ordering but means avatar asset and profile are not referentially linked.
  The client supplies the CDN URL directly on profile update; the users module performs no `media_assets` lookup.
- No account deactivation self-service flow; `status` changes are admin-only actions.
- Username uniqueness is case-sensitive, so `Alice` and `alice` can coexist.
  This is an impersonation vector and is tracked as a security finding; correcting it requires a collision audit and a rename policy before a unique functional index on `lower(username)` can be created.

---

## Section 4: Inter-Module Dependencies

| Dependency | Direction | Nature |
|------------|-----------|--------|
| `auth` | inbound | Auth module owns the `users` row lifecycle (creation, status validation); users module manages profile fields on the same row |
| `media` | outbound | `avatar_url` is a CDN URL sourced from `media_assets`; relationship is by convention, not FK |
| `notification` | inbound | Notification settings on `user_settings` are read by the notification module before dispatching |
| `social` | outbound | `UserServiceImpl` calls `SocialService` for block gating, follow gating, and viewer relationship state |
| `admin` | inbound | Moderation actions mutate `users.status` and write an `admin_actions` audit row |
| all modules | outbound | `UserSummaryService` is the shared batch resolver for embedding user identity in any response |
