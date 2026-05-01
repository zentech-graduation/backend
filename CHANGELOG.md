# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added
- `RedisConfig` in `common/config/` registers a `LettuceClientOptionsBuilderCustomizer` bean that forces RESP2 protocol, resolving `NOAUTH` errors caused by Lettuce 6 sending `HELLO 3` before authentication against a `requirepass`-only Redis server

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
