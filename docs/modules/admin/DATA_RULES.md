# Admin Module — Data Rules

**Implementation status**: Fully implemented with transactional moderation actions, immutable audit queries, REST/OpenAPI endpoints, and unit/integration coverage.

---

## Section 1: Canonical Data

| Table | Key Columns | Notes |
|-------|-------------|-------|
| `admin_actions` | `id`, `admin_id`, `action_type`, `target_user_id`, `target_entity_type`, `target_entity_id`, `report_id`, `reason`, `metadata`, `created_at` | Immutable audit log of every moderation action taken by an admin or moderator. `target_user_id` and `report_id` become NULL if the referenced records are deleted. |

This table cannot be rebuilt from any other source if lost.

---

## Section 2: Derived Data / Cache / Projection

| Data | Location | Rebuilt From | Rebuild Trigger |
|------|----------|--------------|-----------------|
| Action history per admin | Computed at query time | `SELECT` from `admin_actions` where `admin_id = ?` ordered by `created_at DESC` | Query-time |
| Action history per target user | Computed at query time | `SELECT` from `admin_actions` where `target_user_id = ?` | Query-time (index `idx_admin_actions_target`) |

---

## Section 3: Business Rules

### A. Rules Enforced by the Database

| Rule | Enforced By |
|------|-------------|
| `action_type` must be one of the 22 values in `admin_action_type` enum | `admin_action_type` enum |
| `admin_actions` is an append-only log; there is no `updated_at` and no soft delete | Schema design — no such columns |
| `target_user_id` becomes NULL if the target user's account is deleted | `ON DELETE SET NULL` on `target_user_id` FK |
| `report_id` becomes NULL if the associated report is deleted | `ON DELETE SET NULL` on `report_id` FK |
| Deleting the admin user preserves their audit rows and clears the actor reference | `ON DELETE SET NULL` on `admin_id` FK (V29) |
| `metadata` is `JSONB` — no schema enforced at the database level; structure is defined per `action_type` by application convention | `JSONB` column |

### B. Rules Enforced by Application Code

| Rule | Service / Component |
|------|---------------------|
| Only users with `role = 'admin'` or `role = 'moderator'` may create `admin_actions` rows | `SecurityConfig`, `AdminController` |
| Only users with `role = 'admin'` may change an account's `status` | `SecurityConfig` (the `/api/v1/admin/users/**` matcher), method-level `@PreAuthorize` on `AdminController`, and `AdminAuthorizationService` at the service layer |
| Nobody may change their own account's `status` through the API | `AdminAuthorizationService.assertMayChangeUserStatus` |
| Nobody may change an administrator's account `status` through the API, whoever the actor is | `AdminAuthorizationService.assertMayChangeUserStatus` |
| `ban_user` action must update `users.status = 'banned'` in the same transaction | `AdminServiceImpl.banUser` |
| `unban_user` action must update `users.status = 'active'` in the same transaction | `AdminServiceImpl.unbanUser` |
| `suspend_user` action must update `users.status = 'suspended'` in the same transaction, and set `users.suspended_until` when the request carries a duration | `AdminServiceImpl.suspendUser` |
| `unsuspend_user` action must update `users.status = 'active'` in the same transaction, and clear `users.suspended_until` so the reinstatement sweep cannot re-fire on the row | `AdminServiceImpl.unsuspendUser` |
| `change_user_role` action must write `users.role` and revoke the target's refresh tokens in the same transaction | `AdminUserServiceImpl.changeRole` |
| `force_logout` action must revoke every non-revoked `refresh_tokens` row for the target and record the count in `metadata` | `AdminUserServiceImpl.forceLogout` |
| Only the transitions `user -> moderator`, `moderator -> user` and `moderator -> admin` are permitted; an administrator is never a valid target, a skip-level `user -> admin` promotion is refused, and a request naming the role already held is refused | `RoleTransitionPolicy` |
| A moderator reading the audit log sees only rows where `admin_id` equals its own id; an administrator sees every row | `AdminServiceImpl.getActions`, `getActionById`, `getActionsForUser` |
| A lapsed fixed-term suspension returns the account to active and records one `unsuspend_user` row with a null `admin_id` | `SuspensionExpiryServiceImpl`, driven by `UserStateValidator.enforceActive` and `SuspensionExpiryJob` |
| `metadata` is written from server-derived facts only and is never accepted from a request body | `AdminActionRecorder`, `AdminActionRequest` |
| `remove_post` must perform every side effect an owner removal performs, in the same transaction | `AdminServiceImpl.removePost` delegating to `PostService.applyModerationRemoval`. The admin module owns the transition guard and the audit row; the post module owns the side effects, so the administrative and owner removal paths cannot drift apart |
| `restore_post` must return the post to the status it held before the removal, and report that status in the audit row's `metadata.resultingStatus` | `AdminServiceImpl.restorePost` delegating to `PostService.applyModerationRestore` |
| `remove_comment` action must set `comments.deleted_at = NOW()` in the same transaction | `AdminServiceImpl.removeComment` |
| `restore_comment` action must clear `comments.deleted_at` in the same transaction | `AdminServiceImpl.restoreComment` |
| `resolve_report` and `dismiss_report` must update `reports.status` and `reports.reviewed_by` / `reviewed_at` in the same transaction | `AdminServiceImpl.resolveReport`, `AdminServiceImpl.dismissReport` |
| `admin_actions` rows must never be updated or deleted once created; they are the permanent audit trail | `AdminActionRepository` exposes read and insert operations only |

**`admin_id` cascade behavior** `[RESOLVED IN V29]`:
- `admin_actions.admin_id` is nullable and uses `ON DELETE SET NULL`.
- Permanently deleting an administrator preserves their immutable audit history while clearing the actor reference.

### C. Scope Simplifications

- Role-based action restrictions are coarse: an administrator may perform every `action_type`, and a moderator may perform every one except the four account-status transitions (`ban_user`, `unban_user`, `suspend_user`, `unsuspend_user`), `change_user_role`, and `force_logout`.
  The boundary is structural rather than per-action: every administrator-only endpoint lives under `/api/v1/admin/users/`, which a single matcher restricts to ADMIN.
  There is no finer-grained per-action permission model.
- An administrator's account status cannot be changed through the API by anyone, so removing a rogue administrator is a database-level operation.
  This is deliberate.
  A lockout of the whole administrator tier has no in-application recovery path, because a banned account cannot authenticate and unbanning requires authentication; an escalation that requires database access does have one.
- No approval workflow for high-impact actions (e.g., banning a user does not require a second admin to confirm).
- `metadata` JSONB schema per `action_type` is convention-based, not enforced by the database.
- No admin audit log UI; audit data is exposed through role-restricted query endpoints in v1.
- The audit log is row-scoped by actor for a moderator only.
  An administrator's view is unrestricted, and there is no per-target or per-module scoping beyond that.
- `suspended_until` is meaningful only while `status = 'suspended'`, and `status` alone decides the authorization outcome on any request.
  A row with `status <> 'suspended'` and a non-null `suspended_until` is a defect, not a state to interpret.
- Force logout ends refresh capability immediately but not access capability.
  The access-token blacklist is keyed on the token's own `jti`, which no administrator holds, so an access token already issued keeps working for the remainder of `ACCESS_TOKEN_TTL`.
  `WebSocketRevocationSweepService` does not close the target's live realtime sessions either, because it re-validates the access token and that token is still valid.

---

## Section 4: Inter-Module Dependencies

| Dependency | Direction | Nature |
|------------|-----------|--------|
| `users` | inbound | `admin_id` and `target_user_id` reference `users.id`; admin actions mutate `users.status` |
| `report` | inbound | `report_id` links an admin action to the report that prompted it |
| `post` | outbound | `remove_post` / `restore_post` actions mutate `posts.status` and `posts.deleted_at` |
| `comment` | outbound | `remove_comment` / `restore_comment` actions mutate `comments.deleted_at` |
