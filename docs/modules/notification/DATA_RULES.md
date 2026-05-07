# Notification Module — Data Rules

**Implementation status**: Scaffolding only. No Service, Controller, or Repository Java files exist for this module.

---

## Section 1: Canonical Data

| Table | Key Columns | Notes |
|-------|-------------|-------|
| `notifications` | `id`, `recipient_id`, `actor_id`, `type`, `entity_type`, `entity_id`, `is_read`, `read_at`, `created_at` | One row per notification event. `actor_id` is SET NULL if the acting user deletes their account. Polymorphic target via `entity_type` + `entity_id`. |

This table cannot be rebuilt from any other source if lost.

---

## Section 2: Derived Data / Cache / Projection

| Data | Location | Rebuilt From | Rebuild Trigger |
|------|----------|--------------|-----------------|
| Unread notification count | Computed at query time | `COUNT(*)` from `notifications` where `recipient_id = ?` and `is_read = FALSE` | Query-time (partial index `idx_notifications_unread` accelerates this) |
| Notification delivery state | Redis / push service | Cannot be rebuilt — delivery is fire-and-forget | Push delivery event |

---

## Section 3: Business Rules

### A. Rules Enforced by the Database

| Rule | Enforced By |
|------|-------------|
| `type` must be one of the 10 values in `notification_type` enum | `notification_type` enum |
| `is_read` defaults to `FALSE` | `DEFAULT FALSE NOT NULL` |
| `actor_id` becomes NULL if the acting user deletes their account | `ON DELETE SET NULL` on `actor_id` FK |
| Deleting a recipient user cascades to all their notifications | `ON DELETE CASCADE` on `recipient_id` FK |
| There is no `updated_at` column; notifications are immutable after creation | Schema design |
| There is no `deleted_at` column; notifications use hard delete only | Schema design |

### B. Rules Enforced by Application Code

| Rule | Service / Component |
|------|---------------------|
| A notification must not be created if the recipient has disabled the relevant notification type in `user_settings` (e.g., `notify_likes = FALSE`) | `[NOT YET IMPLEMENTED]` |
| A notification must not be created if the actor is blocked by the recipient | `[NOT YET IMPLEMENTED]` |
| A user must not receive a notification for their own actions (e.g., liking their own post) | `[NOT YET IMPLEMENTED]` |
| Marking a notification as read sets `is_read = TRUE` and `read_at = NOW()` | `[NOT YET IMPLEMENTED]` |
| Bulk "mark all as read" updates all unread notifications for the recipient | `[NOT YET IMPLEMENTED]` |
| Push delivery uses `push_tokens` from the users module; token failures must not block the notification write to PostgreSQL | `[NOT YET IMPLEMENTED]` |
| Notification dispatch is done asynchronously via RabbitMQ to avoid blocking the source event transaction | `[NOT YET IMPLEMENTED]` |
| Stale notifications (e.g., for a deleted post) must be handled gracefully on read — `entity_id` may reference a soft-deleted or hard-deleted entity | `[NOT YET IMPLEMENTED]` |

**Failure Mode** `[KNOWN GAP — no retry/DLQ implemented]`:
- Notifications are created as a result of domain events. If event delivery via RabbitMQ fails, the notification is not created.
- There is currently no retry mechanism or dead-letter queue for failed notification events.
- This means some notifications may be silently dropped under failure conditions.

### C. Scope Simplifications

- No real-time WebSocket push in v1. Clients must poll `GET /notifications` to surface new notifications.
- No notification grouping (e.g., "Alice and 5 others liked your post"); each event creates one row.
- No TTL or auto-expiry for notifications; they accumulate indefinitely unless explicitly deleted.
- No `deleted_at` column; notification cleanup, if needed, requires hard deletes.

---

## Section 4: Inter-Module Dependencies

| Dependency | Direction | Nature |
|------------|-----------|--------|
| `users` | inbound | `recipient_id` and `actor_id` reference `users.id`; `user_settings` governs notification preferences |
| `post` | inbound | Notifications reference posts via polymorphic `entity_id` when `entity_type = 'post'` |
| `comment` | inbound | Notifications reference comments via polymorphic `entity_id` when `entity_type = 'comment'` |
| `story` | inbound | Notifications reference stories via polymorphic `entity_id` when `entity_type = 'story'` |
| `social` | inbound | Follow events trigger `follow` and `follow_request` notification types |
| `message` | inbound | Message events trigger `message` notification type |
