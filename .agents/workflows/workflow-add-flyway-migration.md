---
trigger: model_decision
description: Load when adding a new Flyway SQL migration to the project.
---

# Workflow: Add Flyway Migration

## Precondition
- Schema change has been designed and agreed upon.
- `database/schema.sql` reflects the intended final state (or will be updated separately).

## Steps

1. **Determine version** — Run `ls src/main/resources/db/migration/ | sort | tail -5` to find the current highest V-number. Use V{max+1}.

2. **Write the migration file** — Create `src/main/resources/db/migration/V{NN}__{description}.sql`.
   → skill: `skill-flyway-migration.md`

3. **Validate locally** — Start the application to trigger migration:
   ```
   ./mvnw spring-boot:run
   ```
   Confirm Flyway applies the migration without errors. Stop the application.

4. **Run tests** — `./mvnw test`. Tests spin up Testcontainers with a clean database; the new migration must apply and all existing tests must still pass.

5. **Verify `out-of-order`** — Flyway is configured with `out-of-order: true`. Confirm no version gap or collision exists.

6. **Changelog** — Add an entry to `CHANGELOG.md` under `[Unreleased]`.

## Exit criteria
- Migration file follows `V{NN}__{name}.sql` naming with double underscore
- Migration applies cleanly via `./mvnw spring-boot:run`
- `./mvnw test` passes (Testcontainers validates migration from scratch)
- `CHANGELOG.md` updated
