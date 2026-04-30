# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added
- Transactional mail subsystem under `common/mail` backed by the Resend Java SDK with a dedicated `mailTaskExecutor` async pool, exposing email verification, password reset, welcome, and password-changed messages
- Thymeleaf email templates under `templates/mail/` for verification, password reset, welcome, and password-changed notifications
- `app.mail.from-address`, `app.mail.from-name`, and `app.base-url` configuration bound via `MailProperties` and `AppProperties`, all sourced from environment variables
- Single-use, SHA-256-hashed token issuance and consumption in the `auth` module: `EmailVerificationToken` (24h TTL) and `PasswordResetToken` (15min TTL) entities, JPA repositories, and `TokenService` contract
- `MailSendException`, `TokenNotFoundException`, `TokenExpiredException`, `TokenAlreadyUsedException` plus a `GlobalExceptionHandler` mapping them to HTTP 502 / 404 / 410 / 409 with a uniform `ErrorResponse` envelope
- `.env.example` template enumerating every environment variable consumed by the application (database, Redis, JWT, app URL, CORS, Resend mail credentials)
- Unit test coverage for `TokenServiceImpl`, `MailServiceImpl`, and `GlobalExceptionHandler` (21 tests, all passing)
- `CHANGELOG_RULE.md` in `.claude/rules/` defining mandatory post-task changelog requirements following Keep a Changelog 1.1.0
- Post-Task Requirements section in `.claude/rules/AGENT.md` enforcing the changelog obligation after every completed task
- `CHANGELOG_RULE.md` reference in `CLAUDE.md` pre-read list and workflow pipeline comment

### Changed
- `CLAUDE.md` rewritten to accurately describe this project (Spring Boot 4.0.6 social network) — replaced all content copied from an unrelated recruitment ATS project
- `.claude/rules/STRUCT.md` rewritten to reflect the actual codebase: correct technology stack, module roster, database schema, infrastructure services, and domain-specific notes
