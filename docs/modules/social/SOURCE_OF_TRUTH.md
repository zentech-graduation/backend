# Social Module — Source of Truth

**Implementation status**: Scaffolding only. No Service, Controller, or Repository Java files exist for this module.

---

## Section 1: Source-of-Truth Data

| Table | Key Columns | Notes |
|-------|-------------|-------|
| `follows` | `follower_id`, `following_id`, `status`, `created_at` | Unidirectional follow relationships (Instagram-style). `status = 'pending'` when the target account is private and has not yet approved the request. |
| `blocks` | `blocker_id`, `blocked_id`, `created_at` | Block relationships. Bidirectional in effect: blocked user cannot follow, message, or see the blocker's content. |

These tables cannot be rebuilt from any other source if lost.

---

## Section 2: Derived / Secondary Data

| Data | Location | Rebuilt From | Rebuild Trigger |
|------|----------|--------------|-----------------|
| `users.follower_count` | `users` table | `COUNT(*)` from `follows` where `following_id = user.id` and `status = 'accepted'` | Trigger `trg_follow_counts` (V16) |
| `users.following_count` | `users` table | `COUNT(*)` from `follows` where `follower_id = user.id` and `status = 'accepted'` | Trigger `trg_follow_counts` (V16) |
| Pending follow requests view | `pending_follow_requests` (DB view, V17) | Built from `follows` where `status = 'pending'` | Query-time |
| Follow state cache | Redis | Rebuild from `follows` table | Cache miss or TTL expiry |

---

## Section 3: Business Rules

### A. Rules Enforced by the Database

| Rule | Enforced By |
|------|-------------|
| A user cannot follow themselves | `CHECK (follower_id <> following_id)` on `follows` |
| A user cannot block themselves | `CHECK (blocker_id <> blocked_id)` on `blocks` |
| `follows.status` must be one of `'pending'`, `'accepted'`; defaults to `'accepted'` | `follow_status` enum, `DEFAULT 'accepted'` |
| At most one follow relationship per (follower, following) pair | Compound `PRIMARY KEY (follower_id, following_id)` |
| At most one block relationship per (blocker, blocked) pair | Compound `PRIMARY KEY (blocker_id, blocked_id)` |
| Deleting a user cascades to their follow and block rows | `ON DELETE CASCADE` on all FK references |
| `follower_count` and `following_count` on `users` are updated atomically with follow state changes | Trigger `trg_follow_counts` fires on INSERT / UPDATE / DELETE of `follows` |
| Counter increment happens only when `status = 'accepted'`; pending follows do not increment counters | Trigger `fn_follow_counts` logic |

### B. Rules Enforced by Application Code

| Rule | Service / Component |
|------|---------------------|
| When target account has `is_private = TRUE`, a new follow row is created with `status = 'pending'` | `[NOT YET IMPLEMENTED]` |
| When target account has `is_private = FALSE`, a new follow row is created with `status = 'accepted'` | `[NOT YET IMPLEMENTED]` |
| Approving a follow request updates `follows.status` from `'pending'` to `'accepted'` | `[NOT YET IMPLEMENTED]` |
| Declining a follow request deletes the `follows` row | `[NOT YET IMPLEMENTED]` |
| Unfollowing deletes the `follows` row (triggers counter decrement) | `[NOT YET IMPLEMENTED]` |
| Blocking a user must also delete any existing follow rows in both directions | `[NOT YET IMPLEMENTED]` |
| A blocked user must be excluded from follower/following lists, search results, and all feed queries | `[NOT YET IMPLEMENTED]` |
| A follow request generates a `follow_request` notification; an accepted follow generates a `follow` notification | `[NOT YET IMPLEMENTED]` |
| When a private account is made public, all `'pending'` follow rows for that account must be transitioned to `'accepted'` | `[NOT YET IMPLEMENTED]` |

### C. Scope Simplifications

- No mutual-follow (friends) concept; the model is strictly unidirectional.
- Block relationships have no expiry or appeal mechanism.
- No "close friends" or restricted list features.

---

## Section 4: Inter-Module Dependencies

| Dependency | Direction | Nature |
|------------|-----------|--------|
| `users` | inbound | `follower_id`, `following_id`, `blocker_id`, `blocked_id` all reference `users.id`; counters written back to `users` |
| `notification` | outbound | Follow and follow-request events trigger notification creation |
| `post` | inbound | Post visibility (private account) is gated by `follows.status = 'accepted'` |
| `message` | inbound | Messaging permissions depend on whether a block relationship exists |
