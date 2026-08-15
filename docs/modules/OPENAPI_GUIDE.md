# OpenAPI Documentation Guide

## 1. Overview

This project separates OpenAPI documentation concerns from controller logic using the Interface
Segregation pattern. Rather than placing `@Operation`, `@ApiResponses`, `@Tag`, and related
springdoc annotations directly on controller methods — where they mix with routing, validation,
and business logic — each module exposes a dedicated `*Api` interface that carries every
documentation annotation. The controller implements that interface and contains zero documentation
annotations. This keeps controllers readable, makes the API contract explicit in one place, and
prevents documentation code from drifting into framework-specific handler logic.

After reading this guide you will be able to create a `*Api` interface for a new module, declare
all OpenAPI metadata on that interface, update the corresponding controller to implement it, and
annotate your DTOs for accurate schema generation — without touching `OpenApiConfig` or any
existing infrastructure.

---

## 2. Pattern Structure

```
┌──────────────────────────────────────────────────────────────────┐
│  com.app.common.config.openapi.OpenApiConfig                     │
│  (global, created once, never modified per module)               │
│                                                                  │
│  @Bean OpenAPI openApi()                                         │
│    - title, version, server URL                                  │
│    - global "bearerAuth" SecurityScheme + SecurityRequirement    │
└──────────────────────────────────────────────────────────────────┘
            ↑ all operations inherit bearerAuth by default

┌──────────────────────────────────────────────────────────────────┐
│  com.app.modules.{module}.api.{Module}Api           (interface)  │
│                                                                  │
│  @Tag(name = "...", description = "...")                         │
│  @RequestMapping(ApiConstants.{Module}.ROOT)                     │
│  public interface {Module}Api {                                  │
│      @Operation(...)                                             │
│      @ApiResponses(...)                                          │
│      @PostMapping(...)  / @GetMapping(...)                       │
│      ResponseEntity<ApiResponse<T>> someMethod(...);             │
│  }                                                               │
└──────────────────────────────────────────────────────────────────┘
                        ↑ implements

┌──────────────────────────────────────────────────────────────────┐
│  com.app.modules.{module}.controller.{Module}Controller          │
│                                                                  │
│  @RestController                                                 │
│  public class {Module}Controller extends BaseController          │
│          implements {Module}Api {                                │
│                                                                  │
│      @Override                                                   │
│      @PostMapping(...)                                           │
│      @RateLimiter(...)                                           │
│      public ResponseEntity<ApiResponse<T>> someMethod(           │
│              @Valid @RequestBody SomeRequest request) { ... }    │
│  }                                                               │
└──────────────────────────────────────────────────────────────────┘
```

| Component | Package convention | Responsibility |
|-----------|-------------------|----------------|
| `OpenApiConfig` | `com.app.common.config.openapi` | Global metadata and security scheme; one instance for the entire application |
| `{Module}Api` | `com.app.modules.{module}.api` | All OpenAPI annotations: `@Tag`, `@Operation`, `@ApiResponses`, `@Parameter` |
| `{Module}Controller` | `com.app.modules.{module}.controller` | Spring MVC routing, validation, resilience, and business logic delegation; zero documentation annotations |

---

## 3. Step-by-Step: Adding Documentation to a New Module

### Step 1 — Create the `*Api` interface

Create the interface in `com.app.modules.{module}.api.{Module}Api`. The interface receives two
class-level annotations:

- `@Tag` — the group name and description that Swagger UI uses to organise this module's
  endpoints.
- `@RequestMapping` — the base path constant from `ApiConstants`. This is the **only** place the
  base path is declared. Do not repeat it on the controller class.

**Example from `AuthApi`:**

```java
package com.app.modules.auth.api;

import org.springframework.web.bind.annotation.RequestMapping;

import com.app.common.ApiConstants;

import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(
        name = "Authentication",
        description = "Registration, login, token management, and password flows")
@RequestMapping(ApiConstants.Auth.ROOT)
public interface AuthApi {
    // method declarations follow
}
```

The interface must not carry `@RestController`, `@Component`, or any Spring bean annotation.

---

### Step 2 — Declare an endpoint method on the interface

Each method declaration carries:

1. `@Operation` — human-readable `summary` (one sentence) and `description` (longer explanation).
2. `@ApiResponses` — one `@ApiResponse` entry per status code the endpoint can return.
3. The HTTP method mapping annotation (`@PostMapping`, `@GetMapping`, etc.) referencing the path
   constant from `ApiConstants`.
4. `@Parameter` on individual parameters when documentation of a query or path parameter is
   required.

**`@ApiResponse` body schema convention:**

The rule differs between success and error responses, and getting it the wrong way round is the
single most common mistake in this pattern.

**Success responses (`2xx`): omit `@Content` entirely.**
The method already declares `ResponseEntity<ApiResponse<T>>`, and springdoc derives the full
`ApiResponse<T>` envelope from that return type, with `T` resolved into the `data` property.
Writing an explicit `@Content` that names the payload type declares the bare payload as the whole
body, which is factually wrong: the server always wraps it in an envelope.
`OpenApiContractIT.everySuccessResponseDeclaresTheApiResponseEnvelope` fails on exactly that, and
also on naming `ApiResponse.class` here, which erases `T` and leaves a generated client with
`Object` for the payload.

**Error responses (`4xx`, `5xx`): declare `@Content` naming `ApiResponse.class`.**
The return type describes only the success payload, so an error body has no other way to be
declared.
The error envelope carries no typed `data`, so the bare `ApiResponse.class` is correct here.

**No-body responses (`204`): omit the `content` attribute entirely.**
`OpenApiContractIT.noContentResponsesDeclareNoBody` fails on a `204` that declares one.

```java
// Success response returning data — no content block; the return type carries the schema
@io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "201",
        description = "Account created")

// Error response — reference ApiResponse directly
@io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "409",
        description = "Username or email already in use",
        content =
                @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = ApiResponse.class)))

// No-body response (e.g. 204) — omit the content attribute entirely
@io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "204",
        description = "Logged out")
```

> **Import note:** `io.swagger.v3.oas.annotations.responses.ApiResponse` and
> `com.app.common.response.ApiResponse` share the simple name `ApiResponse`. Import
> `io.swagger.v3.oas.annotations.responses.ApiResponses` (the container) and reference
> individual response entries with the fully-qualified name
> `@io.swagger.v3.oas.annotations.responses.ApiResponse(...)` to avoid the conflict.

**Full method example — `register` from `AuthApi`:**

```java
/**
 * Registers a new user, dispatches verification + welcome emails asynchronously, and returns
 * the first session pair.
 */
@Operation(
        summary = "Register a new user",
        description =
                "Creates a user account, sends a verification email asynchronously, and returns"
                        + " an initial access/refresh token pair.",
        security = {})
@ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "201",
            description = "Account created — verification mail events recorded"),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "409",
            description = "Username or email already in use",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "400",
            description = "Validation failure",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "429",
            description = "Rate limit exceeded",
            content =
                    @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class)))
})
@PostMapping(ApiConstants.Auth.REGISTER)
ResponseEntity<ApiResponse<AuthResponse>> register(
        @Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest);
```

The `201` carries no `@Content`, and the `AuthResponse` schema still reaches the document through
the `ResponseEntity<ApiResponse<AuthResponse>>` return type.
Bean-validation failures are answered with `400`, not `422`; see
`OpenApiContractIT.only422IsDocumentedForAKnownUnprocessableOutcome` for the three operations where
`422` is legitimate.

**Example with `@Parameter` — `verifyEmail` from `AuthApi`:**

```java
@GetMapping(ApiConstants.Auth.VERIFY_EMAIL)
ResponseEntity<ApiResponse<Void>> verifyEmail(
        @Parameter(
                        description =
                                "One-time email verification token from the verification link",
                        required = true)
                @RequestParam("token")
                String token);
```

---

### Step 3 — Mark public versus authenticated endpoints

`OpenApiConfig` applies a global `bearerAuth` security requirement to every operation. You must
explicitly opt out for endpoints that do not require a token.

| Endpoint type | What to write on `@Operation` |
|---------------|-------------------------------|
| Public (no token required) | `security = {}` |
| Authenticated (token required) | Omit the `security` attribute — the global requirement is inherited |

**Public endpoint example:**

```java
@Operation(
        summary = "Log in",
        description = "Authenticates credentials and returns an access/refresh token pair.",
        security = {})
```

**Authenticated endpoint example (no override needed):**

```java
@Operation(
        summary = "Log out",
        description =
                "Revokes the supplied refresh token and blacklists the current access token."
                        + " Idempotent.")
```

---

### Step 4 — Update the controller

After the interface exists, update the controller class as follows:

1. Remove `@RequestMapping` from the class declaration — the base path now comes from the
   interface.
2. Remove `@Tag` from the class declaration.
3. Add `implements {Module}Api` to the class declaration. Retain `@RestController` and
   `extends BaseController`.
4. Remove all `@Operation`, `@ApiResponses`, and documentation-only `@Parameter` annotations from
   every handler method.
5. Add `@Override` to every handler method.
6. Retain all routing, validation, and resilience annotations on the handler methods:
   `@PostMapping` / `@GetMapping`, `@RateLimiter`, `@Valid`, `@RequestBody`, `@RequestParam`,
   and `@AuthenticationPrincipal`.

**Before and after — class declaration:**

```java
// Before
@RestController
@RequestMapping(ApiConstants.Auth.ROOT)
@Tag(name = "Authentication", description = "...")
public class AuthController extends BaseController {

// After
@RestController
public class AuthController extends BaseController implements AuthApi {
```

**Before and after — handler method:**

```java
// Before
@Operation(summary = "Register a new user", ...)
@ApiResponses({ ... })
@PostMapping(ApiConstants.Auth.REGISTER)
@RateLimiter(name = "lowTraffic", fallbackMethod = "rateLimit")
public ResponseEntity<ApiResponse<AuthResponse>> register(
        @Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {

// After
@Override
@PostMapping(ApiConstants.Auth.REGISTER)
@RateLimiter(name = "lowTraffic", fallbackMethod = "rateLimit")
public ResponseEntity<ApiResponse<AuthResponse>> register(
        @Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
```

---

### Step 5 — Annotate request and response DTOs

Every request and response DTO must carry `@Schema` annotations so that springdoc generates a
useful schema in the OpenAPI spec.

**Class level** — a `description` attribute summarising what the DTO represents:

```java
@Schema(description = "Credentials for email/password login")
public record LoginRequest(
        // fields follow
) {}
```

**Field level** — a `description`, an `example`, and a `requiredMode` for each field. Use
`Schema.RequiredMode.REQUIRED` for fields the caller must always supply and omit `requiredMode`
(defaulting to `AUTO`) for optional fields:

```java
@Schema(
        description = "Registered email address",
        example = "john@example.com",
        requiredMode = Schema.RequiredMode.REQUIRED)
@NotBlank
@Email
String email,

@Schema(
        description = "Human-readable display name shown on the profile (optional)",
        example = "John Doe")
@Size(max = 100)
String displayName
```

Apply the same pattern to response records:

```java
@Schema(description = "Access/refresh token pair with basic user information")
public record AuthResponse(
        @Schema(
                        description =
                                "Short-lived JWT access token to be sent as Bearer in Authorization header",
                        example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
                String accessToken,
        // remaining fields
) {}
```

---

## 4. Rules and Constraints

- A controller class and its handler methods must contain zero documentation annotations (`@Tag`,
  `@Operation`, `@ApiResponses`, `@ApiResponse`, `@Parameter`, `@Schema` on method parameters).
- `@RequestMapping` for the base path belongs on the `*Api` interface only; it must not appear on
  the controller class.
- `@RestController` belongs on the controller class only; it must never appear on the `*Api`
  interface.
- Runtime annotations — `@RateLimiter`, `@Valid`, `@RequestBody`, `@RequestParam`,
  `@AuthenticationPrincipal` — stay on the controller handler methods; they must not be added to
  the interface (AOP and binding processors operate on the concrete class).
- `@Override` is required on every controller handler method that implements a method declared in
  the `*Api` interface.
- `OpenApiConfig` is global infrastructure and must not be modified when adding a new module; all
  per-module documentation belongs in the module's `*Api` interface.
- The `*Api` interface must not declare default methods, static methods, or any implementation
  logic; it is a pure contract.

---

## 5. Reference: `auth` Module

The following table lists all endpoints declared in `AuthApi` and serves as a concrete example
you can copy from when documenting your own module.

| Method | Path | HTTP | Auth required | Response type |
|--------|------|------|---------------|---------------|
| `register` | `/api/v1/auth/register` | POST | No | `ApiResponse<AuthResponse>` |
| `login` | `/api/v1/auth/login` | POST | No | `ApiResponse<AuthResponse>` |
| `refresh` | `/api/v1/auth/refresh` | POST | No | `ApiResponse<AuthResponse>` |
| `logout` | `/api/v1/auth/logout` | POST | Yes | `ApiResponse<Void>` |
| `verifyEmail` | `/api/v1/auth/verify-email` | GET | No | `ApiResponse<Void>` |
| `resendVerification` | `/api/v1/auth/verify-email/resend` | POST | No | `ApiResponse<Void>` |
| `forgotPassword` | `/api/v1/auth/forgot-password` | POST | No | `ApiResponse<Void>` |
| `resetPassword` | `/api/v1/auth/reset-password` | POST | No | `ApiResponse<Void>` |

Source files:
- Interface: `src/main/java/com/app/modules/auth/api/AuthApi.java`
- Controller: `src/main/java/com/app/modules/auth/controller/AuthController.java`

---

## 6. Common Mistakes

**Mistake: `@RequestMapping` on both the interface and the controller class.**  
Fix: Remove `@RequestMapping` from the controller class. It belongs on the `*Api` interface only.
Spring MVC picks up the base path mapping from the interface when the controller implements it.

**Mistake: Placing `@RateLimiter` (or any Resilience4j annotation) on the interface.**  
Fix: Keep `@RateLimiter` on the controller handler method. Resilience4j AOP proxies operate on
the concrete class; the annotation on an interface method is silently ignored.

**Mistake: Forgetting `security = {}` on a public endpoint.**  
Fix: Any endpoint that does not require a Bearer token must declare `security = {}` inside its
`@Operation`. Without this override, the global `bearerAuth` requirement is inherited and Swagger
UI will show the endpoint as requiring authentication, which misleads API consumers.

**Mistake: Adding `security = {}` to an authenticated endpoint.**  
Fix: Remove the `security` attribute from `@Operation`. Omitting it causes the operation to
inherit the global `bearerAuth` requirement, which is the correct behaviour for protected
endpoints.

**Mistake: Importing `io.swagger.v3.oas.annotations.responses.ApiResponse` directly alongside
`com.app.common.response.ApiResponse`.**  
Fix: Import only `io.swagger.v3.oas.annotations.responses.ApiResponses` (the container annotation)
and reference individual response entries using the fully-qualified name
`@io.swagger.v3.oas.annotations.responses.ApiResponse(...)`.

**Mistake: Omitting `@Override` on controller handler methods.**  
Fix: Every controller method that implements an `*Api` interface method must be annotated with
`@Override`. The compiler will then catch any signature mismatch between the interface declaration
and the implementing method.

**Mistake: Declaring `@Content` on a success response.**  
Fix: Remove it. A `2xx` response's schema comes from the method's `ResponseEntity<ApiResponse<T>>`
return type. Naming the payload type in a `@Content` block declares the bare payload as the whole
body, which the server never sends, and naming `ApiResponse.class` erases `T`. Both fail
`OpenApiContractIT.everySuccessResponseDeclaresTheApiResponseEnvelope`.

**Mistake: Omitting `@Content` and `@Schema` from an error `@ApiResponse`.**  
Fix: Every `4xx` and `5xx` response requires a `@Content(mediaType = "application/json",
schema = @Schema(implementation = ApiResponse.class))` block. The return type describes only the
success payload, so without this the error body has no declared schema at all and the Swagger UI
shows no structure for that status code.

---

## 7. Composed Response Annotations

A response that is identical across many endpoints — the 400 every cursor endpoint returns for a
malformed pagination cursor is the first case — is declared **once** as a composed meta-annotation
under `com.app.common.config.openapi`, rather than hand-written on every `*Api` method. This is a
deliberate exception to the "one `@ApiResponse` entry per status code, declared individually"
convention in section 3, step 2: identical, repeated declarations are exactly what drifts out of
sync one hand-edit at a time, which is the defect class this guide exists to prevent.

**How it works.** `io.swagger.v3.oas.annotations.responses.ApiResponse` is `@Repeatable`, and
springdoc resolves repeatable annotations through Spring's meta-annotation composition
(`AnnotatedElementUtils`), so a custom annotation meta-annotated with a single `@ApiResponse` is
picked up the same as one written directly on the method — including alongside an `@ApiResponses`
block already present on that method. The two sources merge; neither overrides the other, provided
they declare different status codes.

**Example — `CursorErrorResponses`:**

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@io.swagger.v3.oas.annotations.responses.ApiResponse(
        responseCode = "400",
        description = "Malformed cursor",
        content =
                @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = ApiResponse.class)))
public @interface CursorErrorResponses {}
```

Applied on the method, alongside the existing per-endpoint `@ApiResponses` block:

```java
@Operation(summary = "List the caller's conversations", ...)
@ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(
            responseCode = "200",
            description = "Cursor page of conversation summaries")
})
@CursorErrorResponses
@GetMapping(ApiConstants.Messages.ROOT)
ResponseEntity<ApiResponse<CursorPageResponse<ConversationSummaryResponse>>> listMyConversations(...);
```

**When not to use a composed annotation.** If an endpoint's 400 covers more than the common case —
for example, an offset-paginated search endpoint whose 400 also covers a missing or too-short query
parameter — write that endpoint's `@ApiResponse` entry by hand with a description covering every
cause. A composed annotation and a hand-written entry for the same status code on the same method
cannot coexist: OpenAPI responses are keyed by status code, so a second declaration for a code
already present does not merge, it silently competes with the first. `HashtagApi.search`,
`PostApi.searchPosts`, `SocialApi.getBlockedUsers`, and `UserApi.searchUsers` are documented this
way deliberately and must not be converted to `@CursorErrorResponses`.

**Existing composed annotations:**

| Annotation | Declares | Applies to |
|---|---|---|
| `CursorErrorResponses` | `400` — malformed pagination cursor | Every cursor-paginated endpoint whose only 400 cause is a malformed cursor |
