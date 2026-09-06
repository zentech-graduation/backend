---
trigger: model_decision
description: Load when creating or modifying a Spring Data JPA repository interface.
---

# Skill: Spring Repository

## When to use
Creating or editing any interface in `modules/{module}/repository/`.

## Input required
- Entity class and its ID type (always `UUID`)
- Required query operations (soft-delete filters, bulk updates, etc.)

## Steps

1. Declare the interface:
   ```java
   @Repository
   public interface {Entity}Repository extends JpaRepository<{Entity}, UUID> { }
   ```
2. For every query that must exclude soft-deleted rows, use a derived method name:
   ```java
   Optional<User> findByEmailAndDeletedAtIsNull(String email);
   boolean existsByUsernameAndDeletedAtIsNull(String username);
   ```
3. For custom DML (bulk updates, revocations), use `@Modifying @Query` with JPQL:
   ```java
   @Modifying
   @Query("UPDATE Entity e SET e.revokedAt = :now WHERE e.userId = :userId AND e.revokedAt IS NULL")
   int revokeAllActiveByUserId(@Param("userId") UUID userId, @Param("now") OffsetDateTime now);
   ```
   Return `int` when the caller needs the row count (e.g., optimistic concurrency checks).
4. Add Javadoc **only** on `@Query` methods — describe what it does, the expected `@param` semantics, and what the return value means (especially for `int` counts).
5. Do **not** add Javadoc on derived method names (`findBy...`, `existsBy...`, `deleteBy...`).
6. Return types in use:
   - `Optional<T>` — single row that may be absent
   - `List<T>` — unbounded multi-row result
   - `boolean` — existence checks
   - `int` — row count from `@Modifying` operations
   - `Page<T>` — offset-paginated result (when implementing paginated endpoints)
7. Do not write any business logic in the repository.

## Output contract
- File in `modules/{module}/repository/{Entity}Repository.java`
- Interface annotated `@Repository`
- Extends `JpaRepository<{Entity}, UUID>`
- Soft-delete filters applied via `AndDeletedAtIsNull` derived method suffix
- Javadoc only on `@Query` methods
- No `@Transactional` (Spring Data manages transactions)

## Checklist
- [ ] `@Repository` annotation present
- [ ] Extends `JpaRepository<{Entity}, UUID>`
- [ ] Soft-delete queries use `AndDeletedAtIsNull` suffix
- [ ] `@Modifying` present on every `@Query` DML operation
- [ ] `@Param` used on every JPQL named parameter
- [ ] Javadoc written on every `@Query` method
- [ ] No business logic
