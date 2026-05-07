# Report Module — Data Rules

**Implementation status**: Scaffolding only. No Service, Controller, or Repository Java files exist for this module.

---

## Section 1: Canonical Data

| Table | Key Columns | Notes |
|-------|-------------|-------|
| `reports` | `id`, `reporter_id`, `report_type`, `report_reason`, `entity_id`, `description`, `status`, `reviewed_by`, `reviewed_at`, `resolution_note` | One row per user-submitted content flag. Polymorphic target: `report_type` indicates whether `entity_id` references a post, comment, user, story, or message. |

This table cannot be rebuilt from any other source if lost.

---

## Section 2: Derived Data / Cache / Projection

| Data | Location | Rebuilt From | Rebuild Trigger |
|------|----------|--------------|-----------------|
| Pending reports view | `pending_reports` (DB view, V17) | `reports` where `status = 'pending'`, ordered by `created_at ASC` | Query-time |
| Report counts per entity | Computed at query time | `COUNT(*)` from `reports` grouped by `entity_id` and `report_type` | Query-time |

---

## Section 3: Business Rules

### A. Rules Enforced by the Database

| Rule | Enforced By |
|------|-------------|
| `report_type` must be one of `'post'`, `'comment'`, `'user'`, `'story'`, `'message'` | `report_type` enum |
| `report_reason` must be one of the 8 values in `report_reason` enum | `report_reason` enum |
| `status` must be one of `'pending'`, `'reviewing'`, `'resolved'`, `'dismissed'`; defaults to `'pending'` | `report_status` enum, `DEFAULT 'pending'` |
| `reviewed_by` becomes NULL if the reviewing moderator/admin deletes their account | `ON DELETE SET NULL` on `reviewed_by` FK |
| Deleting the reporter cascades to their submitted reports | `ON DELETE CASCADE` on `reporter_id` FK |
| `entity_id` is a raw `UUID` with no enforced FK — it is polymorphic | No referential constraint; application must validate |

### B. Rules Enforced by Application Code

| Rule | Service / Component |
|------|---------------------|
| A user may not report the same entity more than once | `[NOT YET IMPLEMENTED]` — no UNIQUE constraint in schema; must be enforced at service layer |
| A user may not report their own content | `[NOT YET IMPLEMENTED]` |
| `entity_id` must correspond to an existing entity of the declared `report_type`; validate before insert | `[NOT YET IMPLEMENTED]` |
| Transitioning `status` to `'reviewing'` must record `reviewed_by` and `reviewed_at` | `[NOT YET IMPLEMENTED]` |
| Transitioning `status` to `'resolved'` or `'dismissed'` must include a `resolution_note` | `[NOT YET IMPLEMENTED]` |
| Only users with `role = 'moderator'` or `role = 'admin'` may update report status | `[NOT YET IMPLEMENTED]` |
| Resolving a report with action `remove_post` or `ban_user` must be coordinated with the admin module's `admin_actions` log | `[NOT YET IMPLEMENTED]` |

### C. Scope Simplifications

- No deduplication constraint in the database; the application must prevent duplicate reports from the same user on the same entity.
- `entity_id` has no FK enforcement — if the reported entity is deleted before the report is reviewed, the `entity_id` will reference a non-existent row.
- No automatic escalation or SLA on report review time.

---

## Section 4: Inter-Module Dependencies

| Dependency | Direction | Nature |
|------------|-----------|--------|
| `users` | inbound | `reporter_id` and `reviewed_by` reference `users.id` |
| `post` | inbound | Reports with `report_type = 'post'` target a `posts.id` via polymorphic `entity_id` |
| `comment` | inbound | Reports with `report_type = 'comment'` target a `comments.id` |
| `story` | inbound | Reports with `report_type = 'story'` target a `stories.id` |
| `message` | inbound | Reports with `report_type = 'message'` target a `messages.id` |
| `admin` | outbound | Resolved reports reference `admin_actions.report_id` for audit trail |
