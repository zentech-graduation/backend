---
trigger: model_decision
description: Load when implementing a scaffolded (empty) module from scratch.
---

# Workflow: Implement Module

## Precondition
- Module directory exists under `src/main/java/com/app/modules/{module}/` with a `.gitkeep` file.
- `docs/modules/{module}/DATA_RULES.md` exists.

## Steps

1. **Read documentation** — Read `docs/modules/GLOBAL_RULES.md`, then `docs/modules/{module}/DATA_RULES.md`, then `.agents/rules/STRUCT.md`. Do not write a single line of code before completing this step.

2. **Entity** — Create each `@Entity` class.
   → skill: `skill-jpa-entity.md`

3. **Repository** — Create each repository interface.
   → skill: `skill-spring-repository.md`

4. **DTOs** — Create all request and response record types.
   → skill: `skill-dto.md`

5. **Mapper** — Create the mapper interface.
   → skill: `skill-mapstruct-mapper.md`

6. **Service interface** — Define the public service contract with full Javadoc.
   → skill: `skill-spring-service.md`

7. **Service impl** — Implement the service. Throw `AppException` for all domain errors.
   → skill: `skill-spring-service.md`
   → skill: `skill-exception-handling.md` (if new error codes needed)
   → skill: `skill-redis-key.md` (if Redis is used)

8. **Controller** — Implement the REST controller.
   → skill: `skill-rest-controller.md`

9. **Unit tests** — Write unit tests for the service impl.
   → skill: `skill-test-unit.md`

10. **Integration tests** — Write controller integration tests.
    → skill: `skill-test-integration.md`

11. **Flyway migration** — If the module requires schema changes not yet in a migration, add one.
    → skill: `skill-flyway-migration.md`

12. **Run tests** — `./mvnw test`. All tests must pass before proceeding.

13. **Format** — `./mvnw spotless:apply`.

14. **Changelog** — Add an entry to `CHANGELOG.md` under `[Unreleased]`.

## Exit criteria
- All entities, repositories, DTOs, mapper, service, and controller files created
- Unit tests pass
- Integration test passes against real PostgreSQL + Redis containers
- `./mvnw spotless:check` passes
- `CHANGELOG.md` updated
