# OpenAPI Interface Segregation — Implementation Plan

**Scope**: `auth` module only. All other scaffolded modules are out of scope.  
**Pattern**: Every module exposes a `*Api` interface carrying all OpenAPI annotations. The
controller implements that interface and contains zero documentation annotations.

---

## Section 1: Dependency & Configuration

### 1.1 Maven Dependency

`springdoc-openapi-starter-webmvc-ui 3.0.3` is **already present** in `pom.xml`:

```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>3.0.3</version>
</dependency>
```

No dependency change required. `springdoc-openapi 3.x` targets Spring Boot 4 / Spring Framework 7
and is the correct artifact for this stack.

### 1.2 `application-dev.yml` Properties

The following keys are **already present and correctly valued**:

| Key | Value | Status |
|-----|-------|--------|
| `springdoc.api-docs.path` | `/api-docs` | Already set |
| `springdoc.api-docs.enabled` | `true` | Already set |
| `springdoc.swagger-ui.path` | `/swagger-ui` | Already set |
| `springdoc.swagger-ui.enabled` | `true` | Already set |
| `springdoc.swagger-ui.disable-swagger-default-url` | `true` | Already set |

**No changes required to `application-dev.yml`.**

### 1.3 `application-prod.yml` Properties

The following keys are **already present and correctly valued**:

| Key | Value | Status |
|-----|-------|--------|
| `springdoc.api-docs.enabled` | `false` | Already set |
| `springdoc.swagger-ui.enabled` | `false` | Already set |

`springdoc.api-docs.path` and `springdoc.swagger-ui.path` do not need to appear in prod because
they are disabled. **No changes required to `application-prod.yml`.**

### 1.4 `SecurityConfig` Permit-list

`SecurityConfig.securityFilterChain` already permits the following matchers without authentication:

```java
"/api-docs/**",
"/swagger-ui/**",
"/swagger-ui.html"
```

These cover the springdoc JSON endpoint and the Swagger UI SPA. **No changes required to
`SecurityConfig`.**

---

## Section 2: OpenApiConfig Bean

### 2.1 Package Path

```
com.app.common.config.openapi.OpenApiConfig
```

Place inside `common/config/` alongside existing config classes.

### 2.2 Bean Configuration

The `OpenApiConfig` class must produce a single `@Bean` of type `OpenAPI`.

| Item | Value / Source |
|------|----------------|
| API title | `"App API"` |
| API version | `"1.0.0"` |
| API description | `"REST API for the App social network"` |
| Server URL source | `AppProperties.baseUrl()` — injected from `app.base-url` property (`${APP_BASE_URL}`). The `AppProperties` record is already bound in `common/config/app/AppProperties`. |
| Security scheme name | `"bearerAuth"` |
| Security scheme type | `SecurityScheme.Type.HTTP` |
| Security scheme scheme | `"bearer"` |
| Security scheme bearer format | `"JWT"` |
| Global security requirement | `SecurityRequirement` referencing `"bearerAuth"` applied at `OpenAPI` level so all operations inherit it by default; individual endpoints override with `security = {}` to mark themselves as public |

**Skeleton (no implementation code — structural reference only):**

```
@Configuration
class OpenApiConfig {
    // inject AppProperties to read base-url
    @Bean OpenAPI openApi() {
        return new OpenAPI()
            .info(new Info().title("App API").version("1.0.0")
                .description("REST API for the App social network"))
            .addServersItem(new Server().url(appProperties.baseUrl()))
            .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
            .components(new Components().addSecuritySchemes("bearerAuth",
                new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")));
    }
}
```

---

## Section 3: Auth Module — Interface Plan

### 3.1 Package Path

```
com.app.modules.auth.controller.AuthApi
```

Place in the same package as `AuthController` so the controller's `implements AuthApi` declaration
requires no additional import.

### 3.2 Endpoint Table

All paths below are relative to the base mapping `ApiConstants.Auth.ROOT` = `/api/v1/auth`.

| Method | Path constant | Full path | HTTP method | Auth required | Summary |
|--------|--------------|-----------|-------------|---------------|---------|
| `register` | `ApiConstants.Auth.REGISTER` | `/api/v1/auth/register` | POST | No (public) | Register a new user |
| `login` | `ApiConstants.Auth.LOGIN` | `/api/v1/auth/login` | POST | No (public) | Log in |
| `refresh` | `ApiConstants.Auth.REFRESH` | `/api/v1/auth/refresh` | POST | No (public) | Refresh tokens |
| `logout` | `ApiConstants.Auth.LOGOUT` | `/api/v1/auth/logout` | POST | Yes (bearerAuth) | Log out |
| `verifyEmail` | `ApiConstants.Auth.VERIFY_EMAIL` | `/api/v1/auth/verify-email` | GET | No (public) | Verify email address |
| `resendVerification` | `ApiConstants.Auth.RESEND_VERIFY` | `/api/v1/auth/verify-email/resend` | POST | No (public) | Resend verification email |
| `forgotPassword` | `ApiConstants.Auth.FORGOT_PASSWORD` | `/api/v1/auth/forgot-password` | POST | No (public) | Request password reset |
| `resetPassword` | `ApiConstants.Auth.RESET_PASSWORD` | `/api/v1/auth/reset-password` | POST | No (public) | Reset password |

### 3.3 Per-Endpoint Annotation Detail

#### `register` — POST `/api/v1/auth/register`

**Security**: `security = {}` (public — no bearer token required)

**`@ApiResponse` codes**:

| Code | Description |
|------|-------------|
| 201 | Account created; body is `ApiResponse<AuthResponse>` |
| 409 | Username or email already in use; body is `ApiResponse<Void>` |
| 422 | Validation failure; body is `ApiResponse<Map<String,String>>` |
| 429 | Rate limit exceeded; body is `ApiResponse<Void>` |

**`@Parameter` annotations**: none — body is `@RequestBody RegisterRequest`.

---

#### `login` — POST `/api/v1/auth/login`

**Security**: `security = {}` (public)

**`@ApiResponse` codes**:

| Code | Description |
|------|-------------|
| 200 | Authenticated; body is `ApiResponse<AuthResponse>` |
| 401 | Invalid credentials; body is `ApiResponse<Void>` |
| 422 | Validation failure; body is `ApiResponse<Map<String,String>>` |
| 429 | Rate limit exceeded; body is `ApiResponse<Void>` |

**`@Parameter` annotations**: none — body is `@RequestBody LoginRequest`.

---

#### `refresh` — POST `/api/v1/auth/refresh`

**Security**: `security = {}` (public — refresh token is in the request body, not a bearer header)

**`@ApiResponse` codes**:

| Code | Description |
|------|-------------|
| 200 | Tokens rotated; body is `ApiResponse<AuthResponse>` |
| 401 | Refresh token invalid or expired; body is `ApiResponse<Void>` |
| 429 | Rate limit exceeded; body is `ApiResponse<Void>` |

**`@Parameter` annotations**: none — body is `@RequestBody RefreshRequest`.

---

#### `logout` — POST `/api/v1/auth/logout`

**Security**: inherits global `bearerAuth` (no override — authenticated endpoint)

**`@ApiResponse` codes**:

| Code | Description |
|------|-------------|
| 204 | Logged out; no body |
| 401 | Missing or invalid access token; body is `ApiResponse<Void>` |
| 429 | Rate limit exceeded; body is `ApiResponse<Void>` |

**`@Parameter` annotations**: none — body is `@RequestBody RefreshRequest`.

---

#### `verifyEmail` — GET `/api/v1/auth/verify-email`

**Security**: `security = {}` (public)

**`@ApiResponse` codes**:

| Code | Description |
|------|-------------|
| 200 | Email verified; body is `ApiResponse<Void>` |
| 400 | Token invalid or expired; body is `ApiResponse<Void>` |
| 429 | Rate limit exceeded; body is `ApiResponse<Void>` |

**`@Parameter` annotations**:

| Parameter name | In | Required | Description |
|---------------|----|----------|-------------|
| `token` | query | true | One-time email verification token from the verification link |

---

#### `resendVerification` — POST `/api/v1/auth/verify-email/resend`

**Security**: `security = {}` (public)

**`@ApiResponse` codes**:

| Code | Description |
|------|-------------|
| 200 | Email dispatched (or silently ignored if address unknown); body is `ApiResponse<Void>` |
| 429 | Rate limit exceeded; body is `ApiResponse<Void>` |

**`@Parameter` annotations**: none — body is `@RequestBody ResendVerificationRequest`.

---

#### `forgotPassword` — POST `/api/v1/auth/forgot-password`

**Security**: `security = {}` (public)

**`@ApiResponse` codes**:

| Code | Description |
|------|-------------|
| 200 | Reset email dispatched (or silently ignored if address unknown); body is `ApiResponse<Void>` |
| 429 | Rate limit exceeded; body is `ApiResponse<Void>` |

**`@Parameter` annotations**: none — body is `@RequestBody ForgotPasswordRequest`.

---

#### `resetPassword` — POST `/api/v1/auth/reset-password`

**Security**: `security = {}` (public)

**`@ApiResponse` codes**:

| Code | Description |
|------|-------------|
| 200 | Password updated; body is `ApiResponse<Void>` |
| 400 | Token invalid or expired; body is `ApiResponse<Void>` |
| 422 | Validation failure; body is `ApiResponse<Map<String,String>>` |
| 429 | Rate limit exceeded; body is `ApiResponse<Void>` |

**`@Parameter` annotations**: none — body is `@RequestBody ResetPasswordRequest`.

---

### 3.4 Interface-level Annotations

`AuthApi` must carry:

```
@Tag(name = "Authentication", description = "Registration, login, token management, and password flows")
@RequestMapping(ApiConstants.Auth.ROOT)
```

The `@RequestMapping` on the interface ensures Spring MVC inherits the base path when the
controller implements it. No `@RestController` on the interface — that remains on the controller.

---

## Section 4: DTO Annotation Plan

All request and response DTOs in the auth module **already carry `@Schema` annotations** at both
the class level and every field. The table below documents the current state and confirms
completeness. No new annotations are required.

### 4.1 Request DTOs

#### `LoginRequest`

| Field | `description` | `example` | `requiredMode` |
|-------|--------------|-----------|---------------|
| `email` | "Registered email address" | "john@example.com" | REQUIRED |
| `password` | "Account password" | "S3cur3P@ssword" | REQUIRED |

Class-level `@Schema(description = "Credentials for email/password login")` — present.

---

#### `RegisterRequest`

| Field | `description` | `example` | `requiredMode` |
|-------|--------------|-----------|---------------|
| `username` | "Unique username; letters, digits, underscores, and dots only" | "john_doe" | REQUIRED |
| `email` | "User's email address; used for login and verification" | "john@example.com" | REQUIRED |
| `password` | "Account password; 8–128 characters" | "S3cur3P@ssword" | REQUIRED |
| `displayName` | "Human-readable display name shown on the profile (optional)" | "John Doe" | OPTIONAL (no `requiredMode` set — defaults to AUTO/OPTIONAL) |

Class-level `@Schema(description = "Payload for creating a new user account")` — present.

---

#### `RefreshRequest`

| Field | `description` | `example` | `requiredMode` |
|-------|--------------|-----------|---------------|
| `refreshToken` | "Opaque refresh token issued at login or a prior rotation" | "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..." | REQUIRED |

Class-level `@Schema(description = "Payload carrying the refresh token for rotation or logout")` — present.

---

#### `ForgotPasswordRequest`

| Field | `description` | `example` | `requiredMode` |
|-------|--------------|-----------|---------------|
| `email` | "Email address associated with the account" | "john@example.com" | REQUIRED |

Class-level `@Schema(description = "Payload to trigger a password-reset email")` — present.

---

#### `ResetPasswordRequest`

| Field | `description` | `example` | `requiredMode` |
|-------|--------------|-----------|---------------|
| `token` | "One-time reset token from the password-reset email link" | "a1b2c3d4e5f6..." | REQUIRED |
| `newPassword` | "New password to set; 8–128 characters" | "N3wS3cur3P@ss" | REQUIRED |

Class-level `@Schema(description = "Payload to complete a password reset using the one-time token")` — present.

---

#### `ResendVerificationRequest`

| Field | `description` | `example` | `requiredMode` |
|-------|--------------|-----------|---------------|
| `email` | "Email address of the unverified account" | "john@example.com" | REQUIRED |

Class-level `@Schema(description = "Payload to request a new email verification link")` — present.

---

### 4.2 Response DTOs

#### `AuthResponse`

| Field | `description` | `example` | `requiredMode` |
|-------|--------------|-----------|---------------|
| `accessToken` | "Short-lived JWT access token to be sent as Bearer in Authorization header" | "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..." | not set (OPTIONAL by default) |
| `refreshToken` | "Opaque refresh token used to rotate the session" | "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..." | not set |
| `accessTokenExpiresIn` | "Access token lifetime in seconds from the time of issuance" | "900" | not set |
| `tokenType` | "Token scheme; always \"Bearer\"" | "Bearer" | not set |
| `user` | "Summary of the authenticated user" | — | not set |

Class-level `@Schema(description = "Access/refresh token pair with basic user information")` — present.

**Note**: All `AuthResponse` fields are effectively always present on a successful auth response.
Consider setting `requiredMode = Schema.RequiredMode.REQUIRED` on each field for accurate OpenAPI
documentation. This is a documentation improvement, not a behavioural change.

---

#### `UserSummaryResponse`

| Field | `description` | `example` | `requiredMode` |
|-------|--------------|-----------|---------------|
| `id` | "Unique user identifier" | "550e8400-e29b-41d4-a716-446655440000" | not set |
| `username` | "Unique username" | "john_doe" | not set |
| `email` | "User's email address" | "john@example.com" | not set |
| `displayName` | "Display name shown on the profile" | "John Doe" | not set |
| `role` | "User's role in the system" | "user" | not set |
| `emailVerified` | "Whether the user has completed email verification" | "true" | not set |

Class-level `@Schema(description = "Minimal user information included in authentication responses")` — present.

**Same note as `AuthResponse`**: all fields are always populated. Setting `requiredMode =
Schema.RequiredMode.REQUIRED` on each would improve spec accuracy.

---

### 4.3 Fields Flagged as `NEEDS_EXAMPLE`

None. Every field already has either a concrete example or a description sufficient to derive one.

---

## Section 5: Controller Refactor Plan

### 5.1 `AuthController`

**Current class declaration:**
```java
@RestController
@RequestMapping(ApiConstants.Auth.ROOT)
@Tag(name = "Authentication", description = "Registration, login, token management, and password flows")
public class AuthController extends BaseController {
```

**After refactor:**
```java
@RestController
public class AuthController extends BaseController implements AuthApi {
```

**Annotations removed from the class:**

| Annotation | Reason |
|------------|--------|
| `@RequestMapping(ApiConstants.Auth.ROOT)` | Moves to `AuthApi` interface |
| `@Tag(...)` | Moves to `AuthApi` interface |

**Annotations retained on the class:**

| Annotation | Reason |
|------------|--------|
| `@RestController` | Required for Spring MVC to detect the controller; must not be on the interface |

**Annotations removed from handler methods** (all moved to `AuthApi`):

| Method | Removed annotations |
|--------|-------------------|
| `register` | `@Operation`, `@ApiResponses` |
| `login` | `@Operation`, `@ApiResponses` |
| `refresh` | `@Operation`, `@ApiResponses` |
| `logout` | `@Operation`, `@ApiResponses` |
| `verifyEmail` | `@Operation`, `@ApiResponses`, `@Parameter` on `token` param |
| `resendVerification` | `@Operation`, `@ApiResponses` |
| `forgotPassword` | `@Operation`, `@ApiResponses` |
| `resetPassword` | `@Operation`, `@ApiResponses` |

**Annotations retained on handler methods:**

| Annotation type | Retained because |
|-----------------|-----------------|
| `@PostMapping(...)` / `@GetMapping(...)` | Spring MVC routing — must be on the concrete class or interface; keeping on both is the standard Spring pattern. They can remain on the controller or move to the interface — either works. Recommended: keep on **both** (interface for documentation, controller for runtime routing) to avoid Spring MVC limitation where route discovery from interfaces requires explicit MVC config. |
| `@RateLimiter(name = ..., fallbackMethod = ...)` | Resilience4j AOP processes the concrete class; must not be on the interface |
| `@Valid @RequestBody` | Bean Validation processes the concrete class; must not be on the interface |
| `@RequestParam` | Retained on the concrete class for parameter binding; `@Parameter` moves to the interface for documentation |
| Javadoc (`/** ... */`) | Per `COMMENT_STYLE.md`, Javadoc on service interfaces is required; controller handler Javadoc is optional after the interface carries `@Operation`. Existing Javadoc on `AuthController` methods can be removed or kept (no rule prohibits it). |

**Existing inheritance**: `AuthController extends BaseController` — retained unchanged.  
**New inheritance**: `implements AuthApi` added.

---

## Section 6: File Change Summary

| File | Action | Description |
|------|--------|-------------|
| `src/main/java/com/app/common/config/openapi/OpenApiConfig.java` | **Create** | Spring `@Configuration` bean producing the global `OpenAPI` object with title, version, server URL, and `bearerAuth` security scheme |
| `src/main/java/com/app/modules/auth/controller/AuthApi.java` | **Create** | Interface carrying all `@Tag`, `@Operation`, `@ApiResponse`, and `@Parameter` annotations for the 8 auth endpoints |
| `src/main/java/com/app/modules/auth/controller/AuthController.java` | **Modify** | Remove `@Tag`, `@RequestMapping` from class; remove all `@Operation`, `@ApiResponses`, `@Parameter` from handler methods; add `implements AuthApi` |
| `pom.xml` | **Unchanged** | `springdoc-openapi-starter-webmvc-ui 3.0.3` already present |
| `src/main/resources/application-dev.yml` | **Unchanged** | All required springdoc properties already configured |
| `src/main/resources/application-prod.yml` | **Unchanged** | Swagger and api-docs already disabled |
| `src/main/java/com/app/common/security/SecurityConfig.java` | **Unchanged** | `/api-docs/**` and `/swagger-ui/**` already in public permit list |
| All request/response DTOs under `modules/auth/dto/` | **Unchanged** (optional improvement noted) | All `@Schema` annotations already present; optional: add `requiredMode = REQUIRED` to `AuthResponse` and `UserSummaryResponse` fields |

---

## Section 7: Open Questions

1. **`@RequestMapping` placement**: Spring MVC can inherit `@RequestMapping` from an interface
   only when the controller also declares the annotation directly, or when using specific MVC
   configuration. The safe pattern is to keep `@RequestMapping(ApiConstants.Auth.ROOT)` on **both**
   the interface (for springdoc route grouping) and the controller (for runtime routing). The
   implementing agent must decide which strategy to use and verify with a startup test. This plan
   recommends keeping `@RequestMapping` on both.

2. **`requiredMode` on response DTO fields**: `AuthResponse` and `UserSummaryResponse` fields do
   not set `requiredMode`. For accurate spec generation they should be `REQUIRED`. Whether to make
   this change as part of this task or a separate ticket is a human decision.

3. **OAuth2 callback endpoints**: The OAuth2 flow (`/oauth2/authorize`, `/oauth2/callback/*`) is
   handled by Spring Security's built-in `oauth2Login` mechanism, not by a handler method in
   `AuthController`. These paths are not covered by `AuthApi` because there is no corresponding
   controller method to annotate. Decide whether a separate informational `@Operation` entry
   (non-functional, documentation-only) should be added to the interface for discoverability.
