# Report Module — Data Rules

**Implementation status**: Fully implemented with report submission, moderation triage, lifecycle status transitions, and REST/OpenAPI endpoints with unit/integration coverage.

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
| A user may not report the same entity more than once | `ReportServiceImpl.validateDuplicateReport` — enforced via `ReportRepository.existsByReporterIdAndReportTypeAndEntityId`, with the unique index `uq_reports_reporter_type_entity` (V30) as the authoritative guard against a concurrent double-submit |
| A user may not report their own content | `ReportServiceImpl.submitReport` — throws `REPORT_SELF_NOT_ALLOWED` when `reporterId` equals the entity owner |
| `entity_id` must correspond to an existing entity of the declared `report_type`; validate before insert | `ReportServiceImpl.validateEntityExists` — resolves owner via `ReportRepository.findOwnerId`; throws `REPORT_TARGET_NOT_FOUND` if absent |
| `PATCH /reports/{reportId}/status` performs exactly one transition, `pending` to `reviewing`, and records `reviewed_by` and `reviewed_at` | `ReportServiceImpl.validateTransition`, `ReportServiceImpl.updateStatus` |
| Resolving or dismissing a report is refused by `PATCH /reports/{reportId}/status` with `REPORT_INVALID_TRANSITION` | `ReportServiceImpl.validateTransition` — a terminal transition writes an `admin_actions` row, so it belongs to the admin module and giving that row a second, silent writer here is what this refusal prevents |
| Only users with `role = 'moderator'` or `role = 'admin'` may update report status | `SecurityConfig`, `ReportController` — role enforcement via Spring Security |
| Resolving a report with action `remove_post` or `ban_user` must be coordinated with the admin module's `admin_actions` log | Coordination delegated to `AdminServiceImpl.resolveReport` and `AdminServiceImpl.dismissReport` — the admin module is the entry point for resolution actions that carry moderation consequences |

### C. Scope Simplifications

- The application pre-check cannot close the race between two concurrent submissions; the unique index added in V30 is what actually rejects the second one, surfaced as `REPORT_DUPLICATE`.
- `entity_id` has no FK enforcement — if the reported entity is deleted before the report is reviewed, the `entity_id` will reference a non-existent row.
- No automatic escalation or SLA on report review time.
- `resolution_note` is written only by the admin close endpoints. The `resolutionNote` field on the triage request body is retained for wire compatibility and is ignored, because the one transition that endpoint still performs carries no resolution.
- `REPORT_RESOLUTION_NOTE_REQUIRED` is unreachable now that the note requirement lives behind `AdminActionRequest.reason`, which is `@NotBlank`. The constant is kept because an error code is part of the published contract and a client may still branch on it.

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
| `post`, `comment`, `social` | outbound | Each reads `ReportedTargetService` to embed `hasReported` in its viewer state. This is the only Java-level dependency any module has on `report`, and `report` itself imports no other module: its target-existence check reaches `posts`, `comments`, `users`, `stories`, and `messages` through native SQL in `ReportTargetRepositoryImpl`, not through their services. The dependency graph around `report` is therefore acyclic |
