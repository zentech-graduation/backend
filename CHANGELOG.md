# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added
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
