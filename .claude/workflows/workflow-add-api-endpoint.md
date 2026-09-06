---
trigger: model_decision
description: Load when adding a single new endpoint to an already-implemented module.
---

# Workflow: Add API Endpoint

## Precondition
- Module is already implemented (entities, repositories, service, controller exist).
- Business requirements for the new endpoint are understood.

## Steps

1. **Read documentation** — Read `docs/modules/{module}/DATA_RULES.md` and `docs/modules/GLOBAL_RULES.md`. Confirm the operation does not violate any counter, soft-delete, or transactional boundary rule.

2. **Add DTOs** — Create or update request and/or response record types.
   → skill: `skill-dto.md`

3. **Add service interface method** — Add the method signature with full Javadoc to the existing service interface.
   → skill: `skill-spring-service.md`

4. **Implement service method** — Add the implementation to the existing service impl. Add `@Transactional` on the method. Throw `AppException` for domain errors.
   → skill: `skill-spring-service.md`
   → skill: `skill-exception-handling.md`
   → skill: `skill-redis-key.md` (if Redis involved)

5. **Add controller handler** — Add the handler method to the existing controller. Javadoc, `@Operation`, `@ApiResponses`, `@RateLimiter`, `@Valid`.
   → skill: `skill-rest-controller.md`

6. **Add unit test** — Add at least one happy-path and one failure-path test to the existing service impl test class.
   → skill: `skill-test-unit.md`

7. **Add integration test** — Add at least one integration test scenario to the existing controller IT class.
   → skill: `skill-test-integration.md`

8. **Run tests** — `./mvnw test`. All must pass.

9. **Format** — `./mvnw spotless:apply`.

10. **Changelog** — Add an entry to `CHANGELOG.md` under `[Unreleased]`.

## Exit criteria
- New DTOs, service method, and controller handler created
- No counter increment/decrement in service code
- `@Transactional` on the new service impl method
- All tests pass
- `./mvnw spotless:check` passes
- `CHANGELOG.md` updated
