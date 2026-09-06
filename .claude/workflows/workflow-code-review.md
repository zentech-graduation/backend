---
trigger: model_decision
description: Load when reviewing a pull request or verifying completed implementation work.
---

# Workflow: Code Review

## Precondition
- A branch or PR exists with changes to review.

## Steps

1. **Read changed files** — Identify all modified files. For each module touched, read its `docs/modules/{module}/DATA_RULES.md`.

2. **Layer separation** — Verify:
   - Controllers delegate to services only — no repository injection, no entity construction.
   - Services inject repositories, not other services (unless coordinating a cross-module write within one transaction boundary).
   - No `@Transactional` on controllers or repository interfaces.

3. **`@Transactional` placement** — Verify:
   - Present on service impl methods only, not on the interface or the class.
   - Every `@Transactional(propagation = REQUIRES_NEW)` has an inline comment explaining why.

4. **Denormalized counters** — Verify no application code directly sets `follower_count`, `following_count`, `post_count`, `like_count`, `comment_count`, `save_count`, `view_count`, `reply_count`. These must only change via DB triggers.

5. **Response wrapping** — Verify all controller handlers return `ResponseEntity<ApiResponse<T>>` using `ApiResponse.success(...)` or `ApiResponse.failure(...)`.

6. **Exception handling** — Verify domain errors use `AppException(ApiErrorCode.XXX)`. No raw `RuntimeException` thrown with string messages from services.

7. **Javadoc completeness** (per `COMMENT_STYLE.md`):
   - Every `public` service interface method has Javadoc.
   - Every `@RestController` handler method has Javadoc.
   - Every `@Entity` class has class-level Javadoc.
   - Every `@Configuration` class has class-level Javadoc.
   - Every `@Query` repository method has Javadoc.
   - Every `record` response DTO has class-level Javadoc.

8. **Comment style** — Verify:
   - No commented-out code.
   - No decorative dividers (`====`, `----`, `***`).
   - No `TODO`/`FIXME` without a `VR-NNN` issue reference.
   - No attribution comments (`// added by`, `// created by agent`).
   - Inline comments explain WHY, not WHAT.

9. **Soft-delete queries** — Verify all queries against soft-deletable tables (`users`, `posts`, `comments`, `stories`, `messages`) include `deleted_at IS NULL` or use the `AndDeletedAtIsNull` derived method suffix.

10. **Flyway hygiene** — If new tables or columns are added, verify a Flyway migration exists and `database/schema.sql` is NOT used as a migration source.

11. **Test coverage** — Verify:
    - New service methods have at least one unit test for the happy path and one for each failure branch.
    - New controller endpoints have at least one integration test.

12. **Spotless** — Run `./mvnw spotless:check`. If it fails, run `./mvnw spotless:apply` and commit the result.

13. **Changelog** — Verify `CHANGELOG.md` has an entry under `[Unreleased]` for this work.

## Exit criteria
- All checklist items above pass
- `./mvnw test` passes
- `./mvnw spotless:check` passes
- `CHANGELOG.md` has the entry
