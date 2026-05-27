---
trigger: model_decision
description: Load when creating or modifying a service interface or implementation in any module.
---

# Skill: Spring Service

## When to use
Creating or editing files in `modules/{module}/service/` or `modules/{module}/service/impl/`.

## Input required
- Module DATA_RULES.md (read first)
- List of operations the service must expose

## Steps

### Interface (`service/{Entity}Service.java`)
1. Declare a plain Java interface — no annotations on the interface itself.
2. Write Javadoc on **every** public method: one-sentence summary, `@param` for each argument, `@return` for non-void, `@throws AppException` when a specific error code is thrown.
3. Return domain/DTO types directly — do **not** wrap in `ApiResponse<T>` (that is the controller's responsibility).
4. Return `PageResponse<T>` or `CursorPageResponse<T>` for paginated methods.

### Implementation (`service/impl/{Entity}ServiceImpl.java`)
5. Annotate the class: `@Slf4j @Service`
6. Use constructor injection only — no `@Autowired` on fields.
7. Place `@Transactional` on individual methods, **never** on the class.
8. For operations that must commit independently of the caller's transaction, use:
   ```java
   // <one sentence explaining why isolation is required>
   @Transactional(propagation = Propagation.REQUIRES_NEW)
   ```
   The inline comment is mandatory when `REQUIRES_NEW` is used.
9. Throw `AppException(ApiErrorCode.XXX)` for all domain errors. Pick the matching constant from `ApiErrorCode`. Do not throw raw `RuntimeException`.
10. Throw `ApiException(httpStatus, errorCode, message)` only for cases where no `ApiErrorCode` constant exists yet and adding one is out of scope.
11. Do **not** call multiple service methods from within a single service method expecting cross-service atomicity — coordinate within a single transaction boundary.
12. Do **not** increment or decrement denormalized counter columns — those are trigger-maintained.
13. Javadoc on impl methods only if the behavior diverges meaningfully from the interface contract.

## Output contract
- Interface in `service/{Entity}Service.java` — no Spring annotations
- Impl in `service/impl/{Entity}ServiceImpl.java` — `@Slf4j @Service`
- `@Transactional` on individual impl methods only
- Every `REQUIRES_NEW` has an inline comment
- Service returns domain types, not `ApiResponse<T>`
- All domain errors thrown as `AppException(ApiErrorCode.XXX)`

## Checklist
- [ ] Interface has no Spring annotations
- [ ] Javadoc on every interface method
- [ ] Impl class has `@Slf4j @Service`
- [ ] Constructor injection (no field `@Autowired`)
- [ ] `@Transactional` on methods, not class
- [ ] Every `REQUIRES_NEW` has an isolation-reason comment
- [ ] Exceptions thrown as `AppException(ApiErrorCode.XXX)`
- [ ] No counter increment/decrement in application code
- [ ] Service returns domain types (not `ApiResponse<T>`)
