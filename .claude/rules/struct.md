---
trigger: model_decision
description: Load when working on App (social network). Contains the authoritative project map.
---

# Project Structure — App (Social Network)

## 0. Application Purpose

Instagram-style social network: profiles, follow graph, photo/video/carousel posts, likes/saves, nested comments, 24-hour stories, 1-1 and group DMs, hashtags, ranked feed.

Private accounts enforce pending follow requests. Content moderation uses a report system with an admin audit log. A recommendation subsystem tracks behavioral events and interaction scores for feed ranking.

User roles: `user`, `moderator`, `admin`. Architecture: **Modular Monolith**.

---

## 1. Directory Structure

```text
app/
├── .agents/
│   ├── rules/                      # BASE.md, CHANGELOG_RULE.md, COMMENT_STYLE.md, DOC_FIRST.md, STRUCT.md
│   └── skills/                     # Skill definitions
├── .github/
│   ├── workflows/                  # pr-lint.yml (Conventional Commits), pr-size.yml (PR size labels)
│   ├── ISSUE_TEMPLATE/             # bug_report.yml, feature_request.yml, config.yml
│   ├── CODEOWNERS                  # Per-module review ownership
│   └── pull_request_template.md
├── database/
│   └── schema.sql                  # Reference PostgreSQL final-state schema (not applied by Flyway)
├── docker/                         # Scaffolded (.gitkeep)
├── docs/
│   └── modules/
│       ├── GLOBAL_RULES.md         # Cross-module data rules (enum/config table contracts)
│       └── {module}/DATA_RULES.md  # Per-module data access and write rules (13 modules)
├── src/
│   ├── main/
│   │   ├── java/com/app/
│   │   │   ├── common/             # Cross-cutting infrastructure (see §2)
│   │   │   ├── modules/            # 14 domain modules (auth + mail implemented; 12 empty)
│   │   │   └── Application.java    # @SpringBootApplication @ConfigurationPropertiesScan
│   │   └── resources/
│   │       ├── db/migration/       # Flyway V01–V18 SQL migrations
│   │       ├── resilience/
│   │       │   ├── circuitbreaker/ # resilience4j-dev.yml, resilience4j-prod.yml
│   │       │   ├── ratelimiter/    # resilience4j-dev.yml, resilience4j-prod.yml
│   │       │   └── retry/          # resilience4j-dev.yml, resilience4j-prod.yml
│   │       ├── templates/mail/     # email-verification.html, password-changed.html,
│   │       │                       # password-reset.html, welcome.html
│   │       ├── application.yaml    # Core config (active profile: dev)
│   │       ├── application-dev.yml # Dev: JPA show-sql, Swagger at /api-docs, JWT, CORS
│   │       ├── application-prod.yml# Prod: show-sql off
│   │       ├── banner.txt
│   │       └── logback-spring.xml  # Rolling file + console logging
│   └── test/
│       └── java/com/app/
│           ├── ApplicationTests.java                        # Testcontainers context smoke test
│           ├── common/exception/                            # AppException, GlobalExceptionHandler tests
│           ├── common/mail/{config,service/impl,util}/      # MailProperties, MailServiceImpl, MailTemplateRenderer tests
│           ├── common/response/                             # ApiResponse tests
│           ├── common/security/                             # JwtTokenProvider tests
│           ├── common/security/impl/                        # RateLimiter, RefreshToken, TokenBlacklist service tests
│           └── modules/auth/{controller,service/impl}/      # AuthControllerIT, AuthServiceImpl, TokenServiceImpl tests
├── docker-compose.yaml             # Local dev: PostgreSQL, RabbitMQ, Redis
├── pom.xml
├── mvnw / mvnw.cmd
├── AGENTS.md                       # Agent instructions (root-level)
├── CHANGELOG.md
├── CONTRIBUTING.md
├── README.md
├── SECURITY.md
├── .env                            # Local environment variables (gitignored)
└── .env.example                    # Environment variable template
```

---

## 2. Source Code Architecture

### `common/` — Implemented Infrastructure

| Package | Key Classes |
|---------|------------|
| `common/` | `ApiConstants` |
| `common/base/` | `BaseController` |
| `common/config/app/` | `AppProperties` |
| `common/config/mail/` | `MailConfig` |
| `common/config/openapi/` | `OpenApiConfig` |
| `common/config/redis/` | `RedisConfig`, `RateLimitProperties` |
| `common/enums/` | `ApiErrorCode`, `ApiSuccessCode` |
| `common/exception/` | `ApiException`, `AppException`, `GlobalExceptionHandler` |
| `common/response/` | `ApiResponse<T>`, `PageResponse<T>`, `CursorPageResponse<T>` |
| `common/security/` | `SecurityConfig`, `JwtTokenProvider`, `JwtProperties`, `JwtClaims`, `JwtAuthenticationFilter`, `UserPrincipal`, `SecurityMapper`, `SecurityUtils`, `AuthRateLimitFilter`, `CachedBodyHttpServletRequest`, `CorsProperties`, `RateLimiterService`, `RefreshTokenService`, `TokenBlacklistService` |
| `common/security/impl/` | `RateLimiterServiceImpl`, `RefreshTokenServiceImpl`, `TokenBlacklistServiceImpl` |

### Feature Module Layer Pattern

```text
{module}/
├── controller/   # REST endpoints — delegates to Service only
├── service/      # Interface + impl/ (@Transactional on impl methods only)
├── repository/   # Spring Data JPA interfaces
├── entity/       # JPA @Entity classes
├── mapper/       # Entity ↔ DTO conversion
└── dto/          # Request/Response objects (request/, response/ sub-packages)
```

Extra sub-packages in `auth`: `converter/`, `enums/`, `exception/`, `oauth2/` — add as needed per module.

### Feature Module Roster

| Module | Status | Contents |
|--------|--------|----------|
| `auth` | **Implemented** | controller, converter, dto, entity, enums, exception, mapper, oauth2, repository, service/impl |
| `mail` | **Implemented** | config, dto, enums, service/impl, util |
| `users` | Empty (`.gitkeep`) | — |
| `social` | Empty (`.gitkeep`) | — |
| `media` | Empty (`.gitkeep`) | — |
| `post` | Empty (`.gitkeep`) | — |
| `comment` | Empty (`.gitkeep`) | — |
| `hashtag` | Empty (`.gitkeep`) | — |
| `story` | Empty (`.gitkeep`) | — |
| `notification` | Empty (`.gitkeep`) | — |
| `message` | Empty (`.gitkeep`) | — |
| `report` | Empty (`.gitkeep`) | — |
| `admin` | Empty (`.gitkeep`) | — |
| `recommendation` | Empty (`.gitkeep`) | — |

**`auth` responsibilities**: Login, register, OAuth2 (Google/Facebook/Apple), JWT refresh, password reset, email verification.  
**`mail` responsibilities**: Transactional email via SMTP; Thymeleaf templates; `MailTemplate` enum drives template selection.

---

## 3. Infrastructure

### Database

- Engine: **PostgreSQL** (docker-compose: `postgres:latest`)
- Migration: **Flyway** (`out-of-order: true`); 18 migrations at `src/main/resources/db/migration/`:
  - V01 extensions/enums → V02 users/auth → V03 settings/push → V04 social → V05 media → V06 posts → V07 comments → V08 hashtags → V09 stories → V10 notifications → V11 messages → V12 reports → V13 admin → V14 recommendation → V15 indexes → V16 triggers/functions → V17 views → V18 metadata config tables
- Reference schema: `database/schema.sql` (authoritative final-state; not applied by Flyway)
- Extensions: `pgcrypto` (UUID gen), `pg_trgm` (fuzzy username search), `btree_gin` (composite GIN indexes)

PostgreSQL enum types:

| Enum | Values |
|------|--------|
| `user_role` | `user`, `moderator`, `admin` |
| `user_status` | `active`, `suspended`, `deactivated`, `banned` |
| `post_status` | `draft`, `published`, `archived`, `removed` |
| `post_type` | `image`, `video`, `carousel` |
| `media_type` | `image`, `video` |
| `follow_status` | `pending`, `accepted` |
| `story_type` | `image`, `video` |
| `message_type` | `text`, `image`, `video`, `post_share`, `story_share` |
| `report_type` | `post`, `comment`, `user`, `story`, `message` |
| `report_status` | `pending`, `reviewing`, `resolved`, `dismissed` |
| `report_reason` | `spam`, `nudity`, `violence`, `hate_speech`, `harassment`, `false_information`, `scam`, `other` |
| `notification_type` | `like_post`, `like_comment`, `comment_post`, `reply_comment`, `follow`, `follow_request`, `mention_post`, `mention_comment`, `story_view`, `message` |
| `oauth_provider` | `google`, `facebook`, `apple` |
| `admin_action_type` | `ban_user`, `unban_user`, `suspend_user`, `unsuspend_user`, `remove_post`, `restore_post`, `remove_comment`, `restore_comment`, `resolve_report`, `dismiss_report` |
| `event_type` | `post_view`, `post_like`, `post_unlike`, `post_save`, `post_unsave`, `post_share`, `post_comment`, `story_view`, `story_reply`, `profile_view`, `profile_follow`, `profile_unfollow`, `search`, `hashtag_click`, `comment_like`, `comment_reply`, `message_send`, `session_start`, `session_end`, `app_open` |

### Cache — Redis

- docker-compose: `redis:latest`
- Implemented: `RedisConfig`, `RateLimitProperties`, `TokenBlacklistServiceImpl`, `RefreshTokenServiceImpl`, `RateLimiterServiceImpl` (Lua scripts for atomic ops)
- Key patterns in use:
  - `auth:token:email-verification:{sha256}` (TTL 24h) + reverse `auth:token:email-verification:user:{userId}`
  - `auth:token:password-reset:{sha256}` (TTL 15m) + reverse index
  - `auth:blacklist:jti:{jti}` (token blacklist, TTL = remaining access token lifetime)
- Planned key convention: `app:{domain}:{id}` for single entries, `app:{domain}:list` for collections

### Message Broker — RabbitMQ

- docker-compose: `rabbitmq:latest` (port 5672); `spring-boot-starter-amqp` declared
- No exchanges, queues, or bindings defined yet. Add AMQP config to `common/config/` when defining the first queue.

### Resilience

Pre-configured Resilience4j (dev and prod profiles):

| Component | Instance | Config |
|-----------|----------|--------|
| Circuit breaker | `default` | 10-call sliding window, 5 min calls, 50% failure threshold, 10s open wait, 3 half-open calls |
| Rate limiter | `lowTraffic` | 60 req / 30s, 5s timeout |
| Rate limiter | `mediumTraffic` | 120 req / 30s, 5s timeout |
| Rate limiter | `highTraffic` | 120 req / 30s, 5s timeout |
| Retry | `default` | 3 attempts, 1s initial, ×2 backoff |

---

## 4. Technology Stack

| Component | Value |
|-----------|-------|
| Language | Java 21 (virtual threads: `spring.threads.virtual.enabled: true`) |
| Framework | Spring Boot 4.0.6 |
| Database | PostgreSQL |
| Cache | Redis |
| Message broker | RabbitMQ |
| Build | Maven (`./mvnw`) |
| Migrations | Flyway (`spring-boot-starter-flyway`, `flyway-database-postgresql`) |
| Resilience | Resilience4j (Spring Cloud 2025.1.1) |
| Security | Spring Security 6 |
| ORM | Spring Data JPA / Hibernate |
| Code generation | Lombok |
| Observability | Micrometer + Prometheus, datasource-micrometer 2.2.1, Spring Actuator |
| Formatting | Spotless 2.46.1 (Google AOSP); run `./mvnw spotless:apply` |
| Testing | JUnit 5, Testcontainers, Spring Boot test slice starters |
| CI/CD | GitHub Actions (`.github/workflows/pr-lint.yml`, `pr-size.yml`) |

---

## 5. Security

Implemented in `common/security/` and `modules/auth/`:

- **JWT**: `JwtTokenProvider` issues access + refresh tokens. Claims include `jti` (UUID per token, used for blacklisting). TTLs from env vars `ACCESS_TOKEN_TTL`, `REFRESH_TOKEN_TTL`. Signing key from `JWT_SECRET`; issuer from `JWT_ISSUER`.
- **Filter chain**: `JwtAuthenticationFilter` → `AuthRateLimitFilter`. `CachedBodyHttpServletRequest` enables body re-read in filters.
- **Token blacklist**: Redis-backed (`TokenBlacklistServiceImpl`); on logout, `jti` stored until access token expiry.
- **Refresh tokens**: Hashed (`token_hash`) in PostgreSQL with device metadata and IP; revocable via `revoked_at`. Redis-backed creation/consumption (`RefreshTokenServiceImpl`). `rotate()` and `revoke()` use `REQUIRES_NEW` propagation for durable revocation independent of outer transaction.
- **One-time tokens**: Email verification and password reset stored as hashes in Redis with TTL; atomic Lua-script consumption; issuing a new token invalidates the prior one via reverse index.
- **OAuth2**: `CustomOidcUserService`, `OAuth2AuthenticationSuccessHandler`, `OAuth2AuthenticationFailureHandler` in `auth/oauth2/`.
- **Rate limiting**: `AuthRateLimitFilter` uses `RateLimiterServiceImpl` (Redis Lua); instances configured in `resilience/ratelimiter/`.

---

## 6. Key Conventions

- **Naming**: `{Entity}Controller`, `{Entity}Service` / `{Entity}ServiceImpl`, `{Entity}Repository`, `{Entity}Request` / `{Entity}Response`
- **Formatting**: Spotless / Google Java Format AOSP; import order: `java`, `jakarta`, `org`, `com`; 4-space tab indent
- **Transactions**: `@Transactional` on Service impl methods only — never on interfaces or Controllers
- **Responses**: wrap all responses in `ApiResponse<T>`; use `PageResponse<T>` for offset pagination, `CursorPageResponse<T>` for cursor pagination
- **Async**: virtual threads enabled globally
- **Logging**: `timestamp | level | thread | traceId | logger | message`; rolling file 50 MB, 10 files, 500 MB cap
- **Comments**: English only; see `.agents/rules/COMMENT_STYLE.md`

---

## 7. Domain-Specific Invariants

- **Denormalized counters** (maintained by PostgreSQL triggers — never write from application code):
  - `users`: `follower_count`, `following_count`, `post_count`
  - `posts`: `like_count`, `comment_count`, `save_count`, `view_count`
  - `comments`: `like_count`, `reply_count`
  - `hashtags`: `post_count`
  - `stories`: `view_count`
- **`user_events` partitioning**: partitioned by month (`PARTITION BY RANGE (created_at)`). Pre-create monthly partitions before inserting events.
- **Comment depth cap**: `CHECK (depth BETWEEN 0 AND 10)`; adjacency list uses `root_id` for subtree queries.
- **Follow visibility**: insert to `follows` with `is_private = true` target → `status = 'pending'`; counters increment only on `status = 'accepted'` (trigger-enforced).
- **Story expiry**: `expires_at DEFAULT NOW() + INTERVAL '24 hours'`; `idx_stories_expires` implies a cleanup job.
- **`post_interaction_scores`**: written by background scheduler only — not from Controller or Service layers.
- **`user_similarity`**: `user_id_a < user_id_b` constraint eliminates duplicate pairs; populated by batch/ML jobs.
- **Config tables**: `notification_type_configs`, `moderation_action_configs`, `report_reason_configs` (V18) store display metadata for enum values. See `docs/modules/GLOBAL_RULES.md` for the enum/config table contract.
- **Swagger path**: configured at `/api-docs` in `application-dev.yml`.
