---
trigger: model_decision
description: Load when throwing exceptions, adding error codes, or adding module-specific exceptions.
---

# Skill: Exception Handling

## When to use
- Throwing an exception from a service method
- Adding a new error code to `ApiErrorCode`
- Creating a module-specific exception class
- Reviewing how `GlobalExceptionHandler` handles a new exception type

## How the chain works

```
Service throws AppException(ApiErrorCode.XXX)
    → GlobalExceptionHandler.handleAppException
        → ResponseEntity with errorCode.getHttpStatus() + ApiResponse.failure(errorCode, message, null)
```

## `AppException` — use this for all domain errors

```java
throw new AppException(ApiErrorCode.AUTH_INVALID_CREDENTIALS);
// or with a custom message:
throw new AppException(ApiErrorCode.NOT_FOUND, "User " + userId + " not found");
```

- `AppException` carries an `ApiErrorCode` which encodes both the HTTP status and the machine-readable error string.
- The global handler picks this up automatically — no `try/catch` in controllers.
- Always use an existing `ApiErrorCode` constant. Add a new constant if none fits.

## `ApiException` — use sparingly

```java
throw new ApiException(409, "CUSTOM_CODE", "message");
```

- Use only when `ApiErrorCode` is insufficient and adding a constant is out of scope.
- `GlobalExceptionHandler` does **not** currently handle `ApiException` — add a handler if needed.

## Adding a new `ApiErrorCode` constant

Add to `common/enums/ApiErrorCode.java` under the appropriate section:
```java
MODULE_SPECIFIC_ERROR("MODULE_SPECIFIC_ERROR", "Human-readable default message", HttpStatus.XXX),
```
Group by module. Follow the `UPPER_SNAKE_CASE` naming convention.

## Module-specific exceptions

Used when a service-internal exception must travel through layers before being converted to `AppException`. Example: `TokenNotFoundException`, `TokenExpiredException` in `auth/exception/`.

Rules:
- Extend `RuntimeException` (unchecked).
- Add Javadoc.
- Convert to `AppException` at the service boundary **before** the exception reaches a controller, unless `GlobalExceptionHandler` has an explicit handler for it.
- `TokenNotFoundException` / `TokenExpiredException` are the only module exceptions handled directly in `GlobalExceptionHandler` (for the email-verification flow). All other module exceptions must be converted.

## Output contract
- Domain errors thrown as `AppException(ApiErrorCode.XXX)`
- New `ApiErrorCode` entries added with HTTP status, code string, and default message
- Module exceptions converted to `AppException` at service layer (except `TokenNotFoundException`/`TokenExpiredException`)
- No `try/catch` in controllers

## Checklist
- [ ] `AppException(ApiErrorCode)` used for all domain errors
- [ ] Correct `ApiErrorCode` selected (HTTP status matches business intent)
- [ ] New `ApiErrorCode` added under the correct module section
- [ ] Module exceptions converted at service layer, not in controller
- [ ] No raw `RuntimeException` thrown with a string message
