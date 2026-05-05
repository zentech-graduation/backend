# Admin Module — Source of Truth

**Implementation status**: Scaffolding only. No Service, Controller, or Repository Java files exist for this module.

---

## Section 1: Source-of-Truth Data

| Table | Key Columns | Notes |
|-------|-------------|-------|
| `admin_actions` | `id`, `admin_id`, `action_type`, `target_user_id`, `target_entity_type`, `target_entity_id`, `report_id`, `reason`, `metadata`, `created_at` | Immutable audit log of every moderation action taken by an admin or moderator. `target_user_id` and `report_id` become NULL if the referenced records are deleted. |

This table cannot be rebuilt from any other source if lost.

---

## Section 2: Derived / Secondary Data

| Data | Location | Rebuilt From | Rebuild Trigger |
|------|----------|--------------|-----------------|
| Action history per admin | Computed at query time | `SELECT` from `admin_actions` where `admin_id = ?` ordered by `created_at DESC` | Query-time |
| Action history per target user | Computed at query time | `SELECT` from `admin_actions` where `target_user_id = ?` | Query-time (index `idx_admin_actions_target`) |

---

## Section 3: Business Rules

### A. Rules Enforced by the Database

| Rule | Enforced By |
|------|-------------|
| `action_type` must be one of the 10 values in `admin_action_type` enum | `admin_action_type` enum |
| `admin_actions` is an append-only log; there is no `updated_at` and no soft delete | Schema design — no such columns |
| `target_user_id` becomes NULL if the target user's account is deleted | `ON DELETE SET NULL` on `target_user_id` FK |
| `report_id` becomes NULL if the associated report is deleted | `ON DELETE SET NULL` on `report_id` FK |
| Deleting the admin user cascades to their `admin_actions` rows | `ON DELETE CASCADE` on `admin_id` FK |
| `metadata` is `JSONB` — no schema enforced at the database level; structure is defined per `action_type` by application convention | `JSONB` column |

### B. Rules Enforced by Application Code

| Rule | Service / Component |
|------|---------------------|
| Only users with `role = 'admin'` or `role = 'moderator'` may create `admin_actions` rows | `[NOT YET IMPLEMENTED]` |
| `ban_user` action must update `users.status = 'banned'` in the same transaction | `[NOT YET IMPLEMENTED]` |
| `unban_user` action must update `users.status = 'active'` in the same transaction | `[NOT YET IMPLEMENTED]` |
| `suspend_user` action must update `users.status = 'suspended'` in the same transaction | `[NOT YET IMPLEMENTED]` |
| `unsuspend_user` action must update `users.status = 'active'` in the same transaction | `[NOT YET IMPLEMENTED]` |
| `remove_post` action must set `posts.status = 'removed'` and `posts.deleted_at = NOW()` in the same transaction | `[NOT YET IMPLEMENTED]` |
| `restore_post` action must clear `posts.deleted_at` and reset `posts.status = 'published'` in the same transaction | `[NOT YET IMPLEMENTED]` |
| `remove_comment` action must set `comments.deleted_at = NOW()` in the same transaction | `[NOT YET IMPLEMENTED]` |
| `restore_comment` action must clear `comments.deleted_at` in the same transaction | `[NOT YET IMPLEMENTED]` |
| `resolve_report` and `dismiss_report` must update `reports.status` and `reports.reviewed_by` / `reviewed_at` in the same transaction | `[NOT YET IMPLEMENTED]` |
| `admin_actions` rows must never be updated or deleted once created; they are the permanent audit trail | `[NOT YET IMPLEMENTED]` — consider a DB-level revoke of UPDATE/DELETE on this table in production |

### C. Scope Simplifications

- No role-based action restrictions beyond `admin` vs `moderator` — both roles can currently perform all `action_type` values.
- No approval workflow for high-impact actions (e.g., banning a user does not require a second admin to confirm).
- `metadata` JSONB schema per `action_type` is convention-based, not enforced by the database.
- No admin audit log UI; data is queryable via SQL only in v1.

---

## Section 4: Inter-Module Dependencies

| Dependency | Direction | Nature |
|------------|-----------|--------|
| `users` | inbound | `admin_id` and `target_user_id` reference `users.id`; admin actions mutate `users.status` |
| `report` | inbound | `report_id` links an admin action to the report that prompted it |
| `post` | outbound | `remove_post` / `restore_post` actions mutate `posts.status` and `posts.deleted_at` |
| `comment` | outbound | `remove_comment` / `restore_comment` actions mutate `comments.deleted_at` |
