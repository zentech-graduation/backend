 # Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Fixed
- Registration now rejects an email or username that belongs to a soft-deleted account with a 409 domain error instead of propagating a database unique-constraint violation as a 500.
- OAuth2 sign-in no longer attempts to create a new account when the provider email matches a soft-deleted user; a 409 domain error is returned instead.
- Username generation for new OAuth2 users now checks the full `users` table (not just non-deleted rows), consistent with the table-wide `UNIQUE` constraint on `users.username`.

### Added
- `UserRepository` exposes table-wide `existsByEmail`, `existsByUsername`, and `findByEmail` methods aligned with the database `UNIQUE` constraints that have no soft-delete partial index.
- `POST /api/v1/auth/oauth2/exchange` back-channel endpoint: redeems a one-time opaque exchange code for a standard access/refresh token pair; rate-limited via `lowTraffic` Resilience4j instance and Redis sliding-window filter.
- `OAuth2ExchangeCodeService` with a Redis-backed implementation that stores 32-byte hex exchange codes under `auth:oauth2:exchange:{code}` (TTL 120 s) and consumes them atomically via a GET-then-DEL Lua script.
- `OAuth2ExchangeRequest` DTO with `@Schema` annotations for the new exchange endpoint.
- `AUTH_OAUTH2_EXCHANGE_CODE_INVALID` error code (HTTP 400) returned when an exchange code is absent or expired.

### Changed
- `OAuth2AuthenticationSuccessHandler` no longer writes tokens into the callback response body; instead generates an exchange code and redirects the browser to `{frontendBaseUrl}/oauth2/callback?code={code}`, eliminating token exposure in the browser redirect (resolves AUTH-012).
- `AuthService` extended with `exchangeOAuth2Code` to support the new back-channel exchange flow.

### Security
- Resolved AUTH-012 (CWE-598, MEDIUM): OAuth2 tokens are no longer delivered through the browser redirect. The success handler now issues a short-lived opaque exchange code and completes the handshake via the authenticated back-channel `POST /api/v1/auth/oauth2/exchange` endpoint.

### Added
- Users module: `UserService`, `UserController`, and `UserApi` implementing profile view and update, public profile lookup with private-account enforcement, and settings view and update endpoints.

### Documentation
- `UserProfileResponse` and `PublicUserProfileResponse` Javadoc and `@Schema` descriptions now explicitly distinguish `isVerified` (administrator-granted platform badge, `users.is_verified`, always `false` for regular users) from email confirmation status (`user_credentials.email_verified`, exposed as `emailVerified` in the auth response). Investigation confirmed `isVerified` returning `false` after email verification is correct behavior — the two fields are unrelated.
- Users module: `User` and `UserSettings` JPA entities, `UserRepository` and `UserSettingsRepository` moved from the auth module to `com.app.modules.users`.
- Users module: `UserRole`, `UserStatus` enums and their JPA converters moved from the auth module to `com.app.modules.users`.
- `GET /api/v1/users/me` — returns the authenticated user's full profile.
- `PATCH /api/v1/users/me` — partial profile update with username uniqueness enforcement.
- `GET /api/v1/users/{userId}` — public profile lookup; private accounts return 401; counter fields omitted for unauthenticated callers.
- `GET /api/v1/users/me/settings` — returns the authenticated user's notification and privacy settings.
- `PATCH /api/v1/users/me/settings` — partial settings update with patch semantics.
- RabbitMQ topology now declares the `social.events` topic exchange, `social.events.dlx`, `mail.queue`, and `mail.dlq` for mail side-effect events only.
- RabbitMQ environment variables are documented in the environment template with publisher confirms and returns enabled.
- Transactional outbox storage now records versioned domain event envelopes in PostgreSQL before RabbitMQ publishing.
- Scheduled outbox publisher now publishes due events to RabbitMQ with correlated publisher confirms, bounded retry metadata, and dead-letter terminal state.
- Consumer inbox storage now records processed message IDs for idempotent RabbitMQ consumers.
- Auth registration, verification resend, password reset, password changed, and OAuth-only reset flows now record mail side-effect events through the transactional outbox instead of sending mail directly.
- Forgot-password handling now records durable outbox events inside a short transaction and applies a configurable response-time floor after the transaction to reduce account-enumeration timing signals.
- Auth mail RabbitMQ consumer now processes `mail.queue` events with manual ack, idempotent inbox deduplication, bounded retry, and DLQ routing.
- Synchronous Resend mail sender added for RabbitMQ consumers while keeping the existing async mail facade for non-consumer callers.

### Fixed
- `UserMapper.toProfileResponse` and `toPublicProfileResponse` now correctly map `isPrivate` and `isVerified` from the `User` entity; previously, MapStruct's JavaBeans convention stripped the `is` prefix from the boolean getter names (`isPrivate()` → property `private`, `isVerified()` → property `verified`), which did not match the record constructor parameter names (`isPrivate`, `isVerified`), causing both fields to silently default to `false` in every response.
- `UserServiceImpl.getUserProfile` no longer rejects authenticated callers viewing a private account; the visibility guard now reads `if (user.isPrivate() && !isAuthenticated)` so only unauthenticated requests receive HTTP 401 for private profiles.
- `UserStateValidator.enforceEmailVerified` now throws `AppException(AUTH_EMAIL_NOT_VERIFIED)` instead of `AUTH_ACCOUNT_INACTIVE`, giving callers a dedicated, distinguishable error code for the unverified-email case.
- `AuthServiceImpl.resetPassword` catch clause extended to `TokenNotFoundException | TokenExpiredException` so an expired password-reset token is mapped to `AUTH_RESET_TOKEN_INVALID` (HTTP 400) rather than propagating as an unhandled exception, mirroring the `verifyEmail` flow.
- Default async executor selection is explicit when scheduled outbox publishing is enabled.
- RabbitMQ template mandatory publishing is enabled so unroutable outbox messages can be detected by publisher returns.
- Outbox publisher now uses short transactional claim leases and per-event state commits instead of holding one batch transaction across RabbitMQ publisher confirms.

### Changed
- `User` and `UserSettings` entities, enums, converters, and repositories relocated from `com.app.modules.auth` to `com.app.modules.users`; all auth module import references updated accordingly.
- Auth mail side-effect documentation now points to outbox events and the mail consumer token-generation flow; raw verification/reset tokens are no longer created in the auth request path.
- Auth mail events now use `actorId = null` for unauthenticated verification resend and forgot-password requests while keeping `aggregateId` as the target user id.
- Auth mail RabbitMQ bindings now live with the auth module event contracts instead of the shared RabbitMQ infrastructure config.

### Tests
- Added RabbitMQ topology tests covering active mail queues, mail event bindings, dead-letter binding, and inactive future queues.
- Added outbox enqueue tests covering event metadata, JSON payload envelope, required field validation, and absence of direct RabbitMQ publishing.
- Added outbox publisher tests covering confirm success, publish failure retry scheduling, max-attempt dead state, nack handling, timeout handling, and database publisher updates.
- Added RabbitMQ Testcontainer coverage for outbox publisher delivery and unroutable-message retry behavior.
- Added outbox claim-lease tests covering expired `PROCESSING` event reclaim and stale-claim result guards.
- Added processed-message inbox tests covering first processing, duplicate skipping, and rollback on failed processing.
- Added auth mail-event tests covering minimal outbox payloads and the absence of raw verification/reset token creation in auth mail request paths.
- Added forgot-password event-routing and timing-equalizer tests, plus controller integration coverage that register writes auth mail outbox events without token-bearing payloads.
- Added auth mail consumer unit and RabbitMQ Testcontainer coverage for successful delivery, duplicate skipping, transient retry, invalid payload DLQ routing, and DLQ publish failure requeue behavior.

### CI
- Replaced split SonarCloud Maven steps with a single `verify sonar-maven-plugin:sonar` invocation; added SonarCloud package cache and `GITHUB_TOKEN` env declaration.

### Security
- OAuth2 authorization-request cookie is now signed with HMAC-SHA256 using a secret configured via `app.security.cookie-signing-secret` (`APP_COOKIE_SIGNING_SECRET`); cookies with an absent or invalid signature are silently rejected before deserialization, preventing `state`-parameter forgery (AUTH-027).
- Replaced blanket `csrf.disable()` with `csrf.ignoringRequestMatchers("/api/**")` so CSRF protection remains active on the OAuth2 callback paths (`/login/oauth2/code/**`); Spring Security now verifies the `state` parameter on every callback (AUTH-027).
- Introduced `CookieOAuth2AuthorizationRequestRepository` to store the pending `OAuth2AuthorizationRequest` in an `HttpOnly`, `SameSite=Lax`, `Path=/`, 5-minute cookie instead of the HTTP session, preserving `SessionCreationPolicy.STATELESS` while keeping `state`-parameter CSRF protection intact; the `Secure` flag is enabled only under the `prod` Spring profile (AUTH-027).
- Enforced strict JWT issuer validation (`iss` claim now rejected when absent); added mandatory `audience` (`aud`) claim issuance and validation (AUTH-001, AUTH-024).
- Replaced blanket `/api/v1/auth/**` `permitAll` with explicit per-endpoint security rules; `POST /api/v1/auth/logout` and `POST /api/v1/auth/change-password` now require authentication (AUTH-002).
- `JwtAuthenticationFilter` now rejects non-ACTIVE users (BANNED/SUSPENDED/DEACTIVATED) on every request rather than waiting for access-token expiry (AUTH-003).
- Introduced `IpExtractor` with trusted-proxy whitelist (`app.security.trusted-proxy-cidrs`); `X-Forwarded-For` is honored only for proxies that match the whitelist (AUTH-004).
- Dropped unused plaintext-credential columns (`access_token`, `refresh_token`, `token_expires_at`) from `oauth_accounts` via V19 migration (AUTH-005).
- `forgotPassword` keeps a generic response across unknown/inactive/active code paths; OAuth-only accounts (no local password) receive an informational mail event instead of a reset token (AUTH-006, AUTH-026).
- Email-verification token failures now return `AUTH_VERIFY_TOKEN_INVALID` (400) without leaking internal exception messages; `GlobalExceptionHandler` no longer echoes `TokenNotFoundException`/`TokenExpiredException` messages to clients (AUTH-007).
- `RateLimiterServiceImpl` now fails closed on Redis failures, denying requests rather than allowing brute-force traffic through an outage (AUTH-008).
- `AuthRateLimitFilter` now covers all sensitive auth endpoints (register, login, refresh, forgot-password, reset-password, verify-email, resend-verify); per-method `@RateLimiter` annotations removed from `AuthController` in favor of the single Redis-backed filter (AUTH-009).
- One-time token creation (email verification, password reset) is now atomic via a Lua script; prior get-then-delete-then-set sequence was non-atomic (AUTH-011).
- `CustomOidcUserService` no longer hardcodes `OAuthProvider.GOOGLE`; provider is resolved from the `OidcUserRequest` registration ID with explicit rejection for unwired providers (AUTH-013).
- `OAuth2AuthenticationFailureHandler` no longer echoes the raw exception message to the response body; provider error details are logged server-side at WARN (AUTH-014).
- `CachedBodyHttpServletRequest` now enforces `app.security.max-login-body-bytes` (default 2048); request bodies exceeding the limit return 400 instead of consuming unbounded memory (AUTH-015).
- `RefreshTokenServiceImpl.rotate()` now performs equivalent DB work for unknown vs known-revoked tokens, masking the timing side-channel that previously distinguished these states (AUTH-016).
- One-time tokens and refresh tokens are now generated from `SecureRandom` (32 bytes / 43-char URL-safe Base64) instead of `UUID.randomUUID()` (AUTH-017).
- `TokenBlacklistServiceImpl.blacklist()` now fails explicitly on Redis failure (throws `AppException(INTERNAL_ERROR)`); logout no longer silently leaves an unrevoked access token (AUTH-018).
- All 429 responses now include a `Retry-After` header (filter path uses the matched rule window; `BaseController` fallback uses 30s) (AUTH-019).
- CORS configuration expanded: `Accept`, `Accept-Language`, `X-Requested-With`, `X-Device-ID` are now allowed; `Retry-After` and `X-Total-Count` are exposed (AUTH-020).
- JWT access tokens now include a `nbf` (not-before) claim equal to `iat`, validated by `JwtTimestampValidator` (AUTH-021).
- Strict request body deserialization enabled globally (`spring.jackson.deserialization.fail-on-unknown-properties=true`) plus `@JsonIgnoreProperties(ignoreUnknown = false)` on `RegisterRequest` and `ResetPasswordRequest` (AUTH-022).
- Production profile sets `logging.file.path: /var/log/app` so the `${user.home}` fallback applies only to dev/test (AUTH-023).

### Added
- SonarCloud static analysis integrated into CI: `sonarcloud.yml` workflow runs on every push to `main` and on every pull request targeting `main`; JaCoCo coverage report at `target/site/jacoco/jacoco.xml` is forwarded to SonarCloud for coverage metrics. **Action required:** disable "Automatic Analysis" in SonarCloud project settings (Administration → Analysis Method) to prevent conflicts with this CI-based analysis.
- CI workflow `ci-test.yml` runs the Maven test suite on pull requests targeting `main` or `develop`; job is reporting-only and does not block merges.
- CI secrets audit report saved to `.claude/workspace/ci-secrets-audit.md`; confirmed no GitHub Actions secrets are required — all runtime values are provided by `@ServiceConnection`, `@DynamicPropertySource`, or `@TestPropertySource` in the test classes.
- `OpenApiConfig` bean updated to source the server URL from `AppProperties.baseUrl()` and produce API title `"App API"`, version `"1.0.0"`, and a global `bearerAuth` Bearer JWT security scheme.
- `AuthApi` interface (`modules/auth/api`) carrying all `@Tag`, `@Operation`, `@ApiResponses`, and `@Parameter` OpenAPI annotations for the 8 auth endpoints; `AuthController` implements this interface and contains zero documentation annotations.
- `docs/modules/OPENAPI_GUIDE.md`: developer guide explaining how to document a new module using the Interface Segregation pattern, including step-by-step instructions, rules, a reference endpoint table, and common mistakes.

### Changed
- `AuthController` refactored to implement `AuthApi`; class-level `@Tag` and `@RequestMapping` removed (now on the interface); all `@Operation`, `@ApiResponses`, and `@Parameter` annotations removed from handler methods.
- `GIT_WORKFLOW.md` agent rule documenting branch naming, Conventional Commits format, allowed scopes, PR size labels, and discrepancies found between `CONTRIBUTING.md` and the actual pr-lint/pr-size workflow enforcement.
- 11 agent skills under `.claude/skills/`: `skill-jpa-entity`, `skill-spring-repository`, `skill-spring-service`, `skill-rest-controller`, `skill-mapstruct-mapper`, `skill-dto`, `skill-flyway-migration`, `skill-exception-handling`, `skill-redis-key`, `skill-test-unit`, `skill-test-integration` — each derived from the implemented `auth` and `mail` modules.
- 4 agent workflows under `.claude/workflows/`: `workflow-implement-module`, `workflow-add-flyway-migration`, `workflow-add-api-endpoint`, `workflow-code-review`.
- `DOC_FIRST.md` agent rule mandating that `GLOBAL_RULES.md` and the relevant module `DATA_RULES.md` (plus `STRUCT.md` for new module implementations) are read before any feature implementation or business-logic change.

### Changed
- `base.md`: added missing `description` field to frontmatter.
- `changelog_rule.md`: corrected description (was a copy of struct.md's description); changed trigger from `model_decision` to `always_on` to match the rule's stated requirement.
- `doc_first.md`: corrected description (was a copy of struct.md's description).

### Fixed
- `refresh` flow transaction boundary corrected: `rotate()` and `revoke()` in `RefreshTokenServiceImpl` now use `REQUIRES_NEW` propagation so both the old-token revocation and the new-token revocation commit to the database independently, even when the outer request transaction rolls back after a banned or suspended account check. Previously, the entire transaction rolled back and the original refresh token remained active.

### Fixed
- Banned, suspended, and deactivated users can no longer receive or redeem a password-reset link; `forgotPassword` returns silently and `resetPassword` throws the appropriate locked/inactive error.
- Password-reset email link now uses `mail.frontend-base-url` and the configurable `mail.reset-password-path` property (default `/reset-password`) instead of the backend API URL, making the link functional in a browser.
- `TokenNotFoundException` thrown during password-reset token consumption is now converted to `AppException(AUTH_RESET_TOKEN_INVALID)` at the service layer before reaching `GlobalExceptionHandler`, returning HTTP 400 with the correct error code instead of HTTP 404.

### Added
- `MailProperties.resetPasswordPath` field (default `/reset-password`) bound to `app.mail.reset-password-path` / `MAIL_RESET_PASSWORD_PATH` environment variable, making the frontend reset-form path configurable.

### Added
- Implementation plan for OpenAPI interface segregation pattern in the `auth` module at `docs/plans/openapi-interface-segregation-plan.md`.


- Integrated `springdoc-openapi-starter-webmvc-ui` 3.0.3 for interactive API documentation.
- `OpenApiConfig` bean exposing title, version, server entry from `app.base-url`, and global Bearer JWT security scheme.
- OpenAPI JSON endpoint at `/api-docs` and Swagger UI at `/swagger-ui` (dev profile only; disabled in prod).
- Full `@Tag`, `@Operation`, `@ApiResponses`, and `@SecurityRequirement` annotations on all `AuthController` endpoints.
- `@Schema` annotations with descriptions, examples, and required-mode markers on all `auth` request and response DTOs.
- Swagger UI paths (`/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html`) added to `SecurityConfig` permit list.


- `report_reason_configs` table appended to V18 Flyway migration: stores display metadata and per-`report_type` scope control for all 8 `report_reason` enum values, seeded with one row per reason.
- `GLOBAL_RULES.md`: new `Enum vs. Config Table Relationship` section documenting the contract between PostgreSQL enum columns and config tables (`notification_type_configs`, `moderation_action_configs`, `report_reason_configs`).
- `media/DATA_RULES.md`: server-side metadata validation rule for `media_assets` insert — specifies that the server must validate all client-submitted metadata before creating the record, and that validation failure must leave no orphaned row.

### Security
- LOW: documented a TOCTOU race in `TokenServiceImpl.createToken` (4-step issue flow is non-atomic so concurrent same-user issuance can briefly leak orphan single-use tokens until natural TTL); deferred per Phase 3 unit-test contract that asserts the current non-atomic shape.
- MEDIUM: aligned `TokenNotFoundException` / `TokenExpiredException` HTTP mapping with the audit-mandated contract — verify-email with consumed/expired/unknown tokens now returns HTTP 404 (`NOT_FOUND`) instead of HTTP 400 (`AUTH_RESET_TOKEN_INVALID`), so the three states are indistinguishable to the caller.

### Fixed
- `GlobalExceptionHandler.handleInvalidToken` now maps token-not-found and token-expired exceptions to `ApiErrorCode.NOT_FOUND` (HTTP 404).
- `ApplicationTests.contextLoads` updated to use Testcontainers for PostgreSQL and Redis; Spring Boot 4 autoconfigure exclude paths corrected (`org.springframework.boot.{jdbc,hibernate,data.jpa,flyway,data.redis,amqp}.autoconfigure.*`).
- `AuthControllerIT` annotated with `@AutoConfigureTestRestTemplate` so the `TestRestTemplate` bean is registered under Spring Boot 4 (which moved this autoconfig out of the default `@SpringBootTest` activation set).

### Changed
- Bumped Testcontainers from 1.21.0 to 1.21.4 to ship a docker-java client compatible with Docker Engine ≥ 25 (which requires Docker API ≥ 1.40).
- Maven Surefire now includes `**/*IT.java` in the test phase so the auth integration test runs as part of `./mvnw test`.

### Tests
- Rewrote `TokenServiceImplTest` (Redis-backed): added `consumeEmailVerificationToken_valid_executesLuaScript` to assert the atomic-consume Lua script execution.
- Added `TokenBlacklistServiceImplTest` covering positive / zero / negative TTL stores, key-exists / key-absent / null-Redis-result reads, and null/blank `jti` defensive paths.
- Added `RateLimiterServiceImplTest` covering under-limit, at-limit, over-limit, and null-script-result outcomes.
- Extended `AuthServiceImplTest`: `logout_blacklistsAccessTokenAndRevokesRefreshToken`, `logout_invalidAccessToken_stillRevokesRefreshToken`, `logout_noAuthContext_blacklistsNothingAndRevokesRefreshToken`.
- Extended `JwtTokenProviderTest`: `generateAccessToken_containsJtiClaim`, `generateAccessToken_eachInvocationProducesUniqueJti`, `validateAndParse_returnsJtiInClaims`, `validateAndParse_returnsExpiresAtInClaims`.
- Extended `GlobalExceptionHandlerTest`: `tokenNotFoundException_returnsNotFound`, `tokenExpiredException_returnsNotFound`.
- Extended `AuthControllerIT`: `verifyEmail_invalidToken_returnsNotFound`, `verifyEmail_consumedToken_returns404`, `verifyEmail_unknownToken_returnsSameNotFoundAsConsumed`, `login_logout_reuseAccessToken_returns401` (blacklist), `login_wrongPassword_5timesSameIp_6thReturns429`, `forgotPassword_3timesSameIp_4thReturns429`.
- Test totals: 115 tests run, 0 failures, 0 errors.

### Changed
- Email-verification and password-reset tokens now live in Redis (24h and 15m TTL respectively), keyed by `auth:token:email-verification:{sha256}` and `auth:token:password-reset:{sha256}`; consumption is atomic via Lua and a reverse `…:user:{userId}` index ensures issuing a new token invalidates the prior pending one.
- `TokenService.consumeEmailVerificationToken` / `consumePasswordResetToken` now return the owning `UUID` so callers no longer need a separate hash lookup.

### Removed
- PostgreSQL tables `email_verification_tokens` and `password_reset_tokens` (dropped from `V02__create_users_auth_tables.sql`); their JPA entities and repositories.
- `TokenAlreadyUsedException` — Redis cannot distinguish "expired" from "already used"; both now surface as `TokenNotFoundException` mapped to `AUTH_RESET_TOKEN_INVALID`.

### Added
- Redis-backed JWT access-token blacklist: logout now records the token's `jti` for its remaining lifetime so it cannot authenticate again before its natural expiry.
- Redis-backed sliding-window rate limiter on `POST /api/v1/auth/login` (5/15min, IP+email keyed), `/forgot-password` and `/verify-email/resend` (3/hr, IP keyed), executed via an atomic `INCR`+`EXPIRE` Lua script.
- `app.rate-limit.*` configuration namespace bound to `RateLimitProperties` for per-endpoint limit and window tuning.
- MapStruct 1.6.3 dependency and annotation processor; `lombok-mapstruct-binding` 0.2.0 wires Lombok before MapStruct in both compile and test-compile phases
- `AuthMapper` interface in `modules/auth/mapper/` maps `User` + `emailVerified` boolean to `UserSummaryResponse` via MapStruct Spring component model
- `SecurityMapper` interface in `common/security/` maps `User` entity to `UserPrincipal` security principal via MapStruct Spring component model

### Changed
- Moved `UserRole`, `UserStatus`, and `OAuthProvider` enums from `modules/auth/entity/` to new `modules/auth/enums/` package; all import sites updated
- `AuthServiceImpl` and `OAuth2AuthenticationSuccessHandler` now delegate `User` → `UserSummaryResponse` mapping to the injected `AuthMapper` bean, replacing inline field-by-field construction
- `JwtAuthenticationFilter` delegates `User` → `UserPrincipal` construction to the injected `SecurityMapper` bean

### Fixed
- Resolved `ObjectMapper` bean injection failure in `SecurityConfig`, `OAuth2AuthenticationSuccessHandler`, and `OAuth2AuthenticationFailureHandler` by migrating imports from `com.fasterxml.jackson.databind.ObjectMapper` (Jackson 2.x) to `tools.jackson.databind.ObjectMapper` (Jackson 3.x, required by Spring Boot 4)
- Removed `ObjectMapper` from `SecurityConfig` constructor; injected via `@Autowired` field to break the circular dependency that prevented context startup
- Fixed `ApplicationTests.contextLoads` failure by adding `src/test/resources/application-test.yml` with placeholder env-var values and excluding infrastructure auto-configurations (DataSource, JPA, Flyway, Redis, RabbitMQ) that require live services

### Security
- Hardened JWT decoder: `JwtTokenProvider` now installs a `DelegatingOAuth2TokenValidator` combining `JwtTimestampValidator` with `JwtIssuerValidator(properties.issuer())`, so signature alone is no longer sufficient — the `iss` claim is enforced on decode.
- Closed login timing oracle: `AuthServiceImpl.login` now invokes BCrypt against a precomputed dummy hash on the unknown-email, missing-credential, and null-password-hash branches, eliminating email enumeration via response latency.
- OAuth2 link-by-email gate: `CustomOidcUserService` refuses to attach an OAuth identity to a pre-existing local account unless the IdP confirms `email_verified=true`, preventing OAuth-based account takeover via misconfigured IdPs.
- Refresh-token replay detection: `RefreshTokenRepository.revokeByTokenHash` returns the affected row count; `RefreshTokenServiceImpl.rotate` treats a zero-row update as a concurrent-rotation race and revokes every active session for the user (token theft per OAuth 2.0 BCP).
- Removed CORS wildcard fallback: `SecurityConfig.corsConfigurationSource` no longer falls back to `setAllowedOriginPatterns("*")` when `app.cors.allowed-origins` is empty; an unset origin list is now a deny-all CORS policy, refusing any wildcard combined with `allowCredentials=true`.
- Locked actuator endpoints: only `/actuator/health` is permitted without auth; `/actuator/**` now requires `ROLE_ADMIN`, blocking ordinary authenticated users from reading `/actuator/prometheus`, `/actuator/env`, etc.
- Stopped persisting Google OAuth access token: `OAuthAccount.accessToken` is no longer stored at link time, removing an unused secret from the database-compromise blast radius.

### Fixed
- `CustomOidcUserService.resolveUniqueUsername` random-suffix branch now re-checks uniqueness via `ThreadLocalRandom` and a bounded retry loop, preventing the rare unique-constraint violation that previously surfaced as a 500.

### Tests
- Unit tests added: `JwtTokenProviderTest` (HS256 happy path, expired-token rejection, tampered-signature rejection, wrong-algorithm rejection, wrong-issuer rejection, non-UUID subject rejection), `RefreshTokenServiceImplTest` (issue/hash, TTL, rotate happy path, expired/revoked/unknown rejection, concurrent-rotation theft detection, revoke idempotency, bulk revoke), `AuthServiceImplTest` (register conflicts, register success + mail dispatch, login timing-safe failure shapes, status branches, refresh, logout idempotency, forgot/reset flows).
- Integration test added: `AuthControllerIT` boots the full Spring Boot context against a Testcontainers PostgreSQL container, runs Flyway migrations, and exercises 16 HTTP scenarios covering register, login, refresh rotation/replay, logout, JWT-protected endpoint validation, and email-verification / forgot-password contracts.

### Added
- Google OAuth2 / OIDC sign-in: `OAuthAccount` entity with case-mapping converter, `OAuthAccountRepository`, `CustomOidcUserService` resolving a Google identity to a local user (link by provider id, fall back to email match, otherwise auto-register with `email_verified=true` and no password), `CustomOidcUser` decorator, and JSON `OAuth2AuthenticationSuccessHandler` / `OAuth2AuthenticationFailureHandler` returning the standard `ApiResponse` envelope
- `spring.security.oauth2.client.registration.google.*` configuration block driven by `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, and `APP_BASE_URL` (redirect URI is environment-dependent)
- `SecurityConfig` now installs `oauth2Login(...)` on the existing single `SecurityFilterChain`, mounting authorize/callback under `/api/v1/auth/oauth2/{authorize,callback/*}` with the custom OIDC user service and JSON handlers
- Stateless JWT authentication module under `common/security/` — `JwtProperties`, `JwtTokenProvider` backed by Spring Security's `NimbusJwtEncoder`/`Decoder`, `JwtAuthenticationFilter`, `UserPrincipal`, `SecurityUtils`, `CorsProperties`, and a single-chain `SecurityConfig` enforcing stateless session policy with BCrypt strength 12
- `RefreshTokenService` interface and `RefreshTokenServiceImpl` issuing UUID-based opaque refresh tokens persisted as SHA-256 hashes; supports rotation, single-token revoke, and bulk revoke per user
- `User`, `UserCredential`, `UserSettings`, and `RefreshToken` JPA entities with `UserRole`/`UserStatus` enums and case-mapping `AttributeConverter`s for the lowercase Postgres custom enum types
- `UserRepository`, `UserCredentialRepository`, `UserSettingsRepository`, and `RefreshTokenRepository` Spring Data JPA repositories
- `AuthService` and `AuthServiceImpl` covering register, login, refresh, logout, email verification, resend verification, forgot password, and reset password flows; verification and password change emails dispatched via the existing `MailService`
- `AuthController` exposing `/api/v1/auth/{register,login,refresh,logout,verify-email,verify-email/resend,forgot-password,reset-password}` returning `ApiResponse` envelopes
- New `ApiErrorCode` entries: `USER_EMAIL_ALREADY_EXISTS`, `USER_USERNAME_ALREADY_EXISTS`, `AUTH_RESET_TOKEN_EXPIRED`, `AUTH_RESET_TOKEN_USED`
- `GlobalExceptionHandler` now translates `TokenNotFoundException`, `TokenExpiredException`, and `TokenAlreadyUsedException` into typed 4xx `ApiResponse` failures
- `RedisConfig` in `common/config/` registers a `LettuceClientOptionsBuilderCustomizer` bean that forces RESP2 protocol, resolving `NOAUTH` errors caused by Lettuce 6 sending `HELLO 3` before authentication against a `requirepass`-only Redis server

### Changed
- `app.jwt.*` is the canonical JWT configuration namespace (`secret`, `issuer`, `access-token-ttl`, `refresh-token-ttl`); the obsolete `security.jwt.*` block has been removed from `application-dev.yml` and `application-prod.yml`
- `spring.datasource.hikari.data-source-properties.stringtype=unspecified` enabled to allow VARCHAR binding to Postgres custom enum and `inet` columns
- `Application.java` now uses `@ConfigurationPropertiesScan` so `JwtProperties`, `CorsProperties`, and `AppProperties` bind without per-config registration
- `pom.xml` adds `spring-boot-starter-oauth2-client` and `spring-security-oauth2-jose` to provide `NimbusJwtEncoder`/`NimbusJwtDecoder`

### Changed
- `CLAUDE.md` reduced to a minimal pointer file; all project rules, architecture state, and conventions moved to `.claude/rules/AGENT.md`
- `.claude/rules/AGENT.md` rewritten with full codebase audit results: accurate module status table, complete `common/` class inventory, Flyway migration status (V01–V17), mail module documentation, API response contract, known gaps, and updated environment variable table including `RESEND_API_KEY`

### Added
- `MailProperties` standard class (`@Component` + `@ConfigurationProperties`) at `common/mail/config/` with `fromAddress`, `fromName`, `appName`, and `frontendBaseUrl` fields
- `MailTemplate` enum at `common/mail/enums/` consolidating all template paths and default subjects
- `MailRequest` DTO at `common/mail/dto/` for carrying recipient and template variable data
- `MailTemplateRenderer` component at `common/mail/util/` isolating Thymeleaf rendering from dispatch logic
- `MailService` interface and `MailServiceImpl` at `common/mail/service/` replacing the former `modules/mail/` placement; provider errors now surface as `AppException` with `SERVICE_UNAVAILABLE`
- `app.mail.from-name`, `app.mail.app-name`, and `app.mail.frontend-base-url` properties bound via the new `MailProperties`; `application-prod.yml` reads these from environment variables
- Unit tests for `MailTemplateRenderer` and `MailServiceImpl` covering variable assembly, template dispatch, and exception wrapping

### Changed
- `MailServiceImpl` variable keys renamed from `recipientName` to `toName`; `appName` added as a template variable sourced from `MailProperties`
- Email templates updated to use `toName` and `appName` Thymeleaf variables; hardcoded branding removed
- `MailConfig` removed `MailProperties` from `@EnableConfigurationProperties` since the class is now a `@Component` self-registering bean

### Removed
- `MailProperties` record from `common/config/` replaced by the standard class at `common/mail/config/`
- `MailService`, `MailServiceImpl`, and `MailSendException` from `modules/mail/` relocated into `common/mail/`

### Added
- `ApiErrorCode` enum in `common/response/` with typed HTTP status, machine-readable code, and default message for Common and Auth error groups
- `ApiSuccessCode` enum in `common/response/` with OK, CREATED, ACCEPTED, NO_CONTENT constants
- `AppException` in `common/exception/` backed by `ApiErrorCode`, replacing raw integer status codes with typed error codes at the service layer
- `ApiResponse.success()` and `ApiResponse.failure()` factory overloads accepting `ApiSuccessCode`/`ApiErrorCode` enums with optional custom message and data payload
- `BaseController` resilience fallback methods for rate limiter and circuit breaker using `ApiErrorCode`-backed responses

### Changed
- `GlobalExceptionHandler` rewritten to use `AppException` + `ApiErrorCode` for all handler return paths; validation handlers now collect all field/constraint violations into a `Map<String, String>` returned as `data`
- `MailServiceImpl` expiry constants promoted to `public` for test visibility

### Added
- Generic `ApiException(int statusCode, String errorCode, String message)` in `common/exception/` to decouple service-layer errors from any shared enum catalogue
- Uniform `ApiResponse<T>` envelope under `common/response/` with `ok`, `created`, and `error` static factories and an embedded `Instant` timestamp
- `PageResponse<T>` (offset-based, built from Spring Data `Page`) and `CursorPageResponse<T>` (Relay-spec cursor pagination with embedded `PageInfo`) under `common/response/`
- `ApiConstants` central registry of `/api/v1` route constants for auth, users, posts, comments, stories, social, messages, notifications, hashtags, media, reports, and admin endpoints
- `spring-boot-starter-validation` dependency for `jakarta.validation` support required by the new framework-level exception handler
- Transactional mail subsystem under `common/mail` backed by the Resend Java SDK with a dedicated `mailTaskExecutor` async pool, exposing email verification, password reset, welcome, and password-changed messages
- Thymeleaf email templates under `templates/mail/` for verification, password reset, welcome, and password-changed notifications
- `app.mail.from-address`, `app.mail.from-name`, and `app.base-url` configuration bound via `MailProperties` and `AppProperties`, all sourced from environment variables
- Single-use, SHA-256-hashed token issuance and consumption in the `auth` module: `EmailVerificationToken` (24h TTL) and `PasswordResetToken` (15min TTL) entities, JPA repositories, and `TokenService` contract
- `.env.example` template enumerating every environment variable consumed by the application (database, Redis, JWT, app URL, CORS, Resend mail credentials)
- Unit test coverage for `TokenServiceImpl`, `MailServiceImpl`, and `GlobalExceptionHandler`
- `CHANGELOG_RULE.md` in `.claude/rules/` defining mandatory post-task changelog requirements following Keep a Changelog 1.1.0
- Post-Task Requirements section in `.claude/rules/AGENT.md` enforcing the changelog obligation after every completed task
- `CHANGELOG_RULE.md` reference in `CLAUDE.md` pre-read list and workflow pipeline comment

### Changed
- `GlobalExceptionHandler` rewritten to handle only generic, framework-level exceptions (`ApiException`, validation, malformed request, type mismatch, not found, method not allowed, data integrity, optimistic lock, access denied, authentication, upload size, illegal argument/state, entity not found, transaction system, and a catch-all 500), all responding with `ApiResponse<Void>`
- Token exceptions (`TokenNotFoundException`, `TokenExpiredException`, `TokenAlreadyUsedException`) relocated from `common/exception/` to `modules/auth/exception/` to enforce module ownership of domain errors
- `MailSendException` relocated from `common/exception/` to `common/mail/` alongside the mail service it belongs to
- `GlobalExceptionHandlerTest` rewritten against the new envelope and handler surface
- `CLAUDE.md` rewritten to accurately describe this project (Spring Boot 4.0.6 social network) — replaced all content copied from an unrelated recruitment ATS project
- `.claude/rules/STRUCT.md` rewritten to reflect the actual codebase: correct technology stack, module roster, database schema, infrastructure services, and domain-specific notes

### Removed
- `ErrorResponse` record and the legacy `common/exception/` token and mail exception classes superseded by `ApiException` and the relocated domain exceptions
