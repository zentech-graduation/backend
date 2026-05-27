---
trigger: model_decision
description: Load when creating or modifying a REST controller in any module.
---

# Skill: REST Controller

## When to use
Creating or editing any class in `modules/{module}/controller/`.

## Input required
- Module DATA_RULES.md (read first)
- Service interface methods being exposed
- API base path constant from `ApiConstants`

## Steps

1. Declare the class:
   ```java
   @RestController
   @RequestMapping(ApiConstants.{Module}.ROOT)
   @Tag(name = "...", description = "...")
   public class {Module}Controller extends BaseController { }
   ```
2. Inject the service via constructor only (no `@Autowired` on fields).
3. Write one-sentence Javadoc on every handler method describing the HTTP action, accepted input, and response.
4. Annotate each handler method:
   ```java
   @Operation(summary = "...", description = "...", security = ...)
   @ApiResponses({...})
   @PostMapping(ApiConstants.{Module}.ENDPOINT)
   @RateLimiter(name = "lowTraffic", fallbackMethod = "rateLimit")
   public ResponseEntity<ApiResponse<ReturnType>> methodName(
           @Valid @RequestBody XxxRequest request, ...) { }
   ```
5. Apply `@Valid` to every `@RequestBody` argument.
6. For public (unauthenticated) endpoints, set `security = {}` in `@Operation`. For protected endpoints, add `@SecurityRequirement(name = "bearerAuth")`.
7. Choose the `@RateLimiter` name: `lowTraffic` for auth/write ops, `mediumTraffic` / `highTraffic` for read-heavy endpoints.
8. Wrap responses using the static factory on `ApiResponse<T>`:
   - `ApiResponse.success(ApiSuccessCode.CREATED, body)` → 201
   - `ApiResponse.success(ApiSuccessCode.OK, body)` → 200
   - `ApiResponse.success(ApiSuccessCode.NO_CONTENT)` → 204
9. Return `ResponseEntity<ApiResponse<Void>>` for no-body responses.
10. Delegate **only** to the service — no repository calls, no entity construction, no business logic.
11. Pass `HttpServletRequest httpRequest` as the last parameter when the service needs device/IP metadata (registration, login, refresh).
12. Do **not** add `@Transactional` to controller methods.

## Output contract
- Class annotated `@RestController @RequestMapping @Tag`
- Extends `BaseController`
- Constructor injection
- Javadoc on every handler method
- All `@RequestBody` arguments use `@Valid`
- All responses wrapped in `ApiResponse<T>`
- All handlers annotated with `@Operation @ApiResponses @RateLimiter`
- No business logic, no repository calls

## Checklist
- [ ] `@RestController @RequestMapping @Tag` present
- [ ] Extends `BaseController`
- [ ] Constructor injection (no field `@Autowired`)
- [ ] Javadoc on every handler method
- [ ] `@Valid` on every `@RequestBody`
- [ ] `@Operation` with correct `security` setting (empty `{}` or `@SecurityRequirement`)
- [ ] `@RateLimiter` on every handler
- [ ] Responses use `ApiResponse.success(...)` factory
- [ ] No `@Transactional` on any controller method
- [ ] No direct repository or entity access
