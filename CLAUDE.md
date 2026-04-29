# App — Claude Code Configuration

## Project

Spring Boot 4.0.6 social network backend. Java 21. Group ID: `com.app`, artifact ID: `app`.

Architecture: Modular Monolith with Domain-Driven Design. Package layout:
- `com.app.common/` — cross-cutting infrastructure (planned; currently empty)
- `com.app.modules/` — 13 domain modules (all scaffolded, implementation pending)

Primary database: PostgreSQL. Cache: Redis. Message broker: RabbitMQ.
No Elasticsearch, no AI/ML, no external payment integration.

See `.claude/rules/STRUCT.md` for the authoritative architecture map, module roster, and infrastructure details.

---

## Key Commands

```bash
# Build (no tests)
./mvnw clean package -DskipTests

# Compile only
./mvnw clean compile -q

# Run all tests
./mvnw test

# Run single test class
./mvnw test -Dtest=ClassName

# Auto-format code (Google AOSP)
./mvnw spotless:apply

# Check format without modifying
./mvnw spotless:check

# Run locally (dev profile)
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Start local infrastructure (PostgreSQL, RabbitMQ, Redis)
docker compose up -d

# Stop infrastructure
docker compose down
```

---

## Environment

- Core config: `src/main/resources/application.yaml`
- Dev profile: `src/main/resources/application-dev.yml`
- Prod profile: `src/main/resources/application-prod.yml`
- Resilience4j configs: `src/main/resources/resilience/` (circuit breaker, rate limiter, retry — per profile)
- Local env file: `.env` (auto-imported by Spring at startup via `config.import`)

Required local services (started by `docker compose up -d`):

| Service | Image | Port |
|---------|-------|------|
| PostgreSQL | `postgres:latest` | 5432 |
| RabbitMQ | `rabbitmq:latest` | 5672 |
| Redis | `redis:latest` | 6379 |

Required environment variables:

| Variable | Purpose |
|----------|---------|
| `POSTGRES_URL` | PostgreSQL JDBC URL |
| `POSTGRES_USER` | Database username |
| `POSTGRES_PASSWORD` | Database password |
| `REDIS_HOST` | Redis hostname |
| `REDIS_PORT` | Redis port |
| `REDIS_PASSWORD` | Redis password |
| `JWT_SECRET` | HMAC signing key for JWT tokens |
| `ACCESS_TOKEN_TTL` | Access token lifetime |
| `REFRESH_TOKEN_TTL` | Refresh token lifetime |
| `JWT_ISSUER` | JWT issuer claim |
| `APP_BASE_URL` | Base URL for Swagger server + OAuth2 callbacks |
| `CORS_ALLOWED_ORIGINS` | Comma-separated list of allowed CORS origins |

---

## Agent System

AgentKit submodule at `.claude/`. 2-layer system.

**Core (AgentKit submodule)**: `agents/`, `skills/`, `workflows/` — stack-agnostic

**Project overlay (this repo)**:
- `.claude/rules/STRUCT.md` — authoritative project map (load before any implementation)
- `.claude/rules/COMMENT_STYLE.md` — Java comment and Javadoc enforcement
- `.claude/rules/AGENT.md` — routing protocol and code generation standards (P0 priority)
- `.claude/rules/BASE.md` — communication style

**Workflow pipeline**:
```
/brainstorm → /plan → /create → /test → /review → /enhance → /debug → /docs → /commit
```

**Before implementing anything**, read:
1. `.claude/rules/STRUCT.md` — project structure ground truth
2. `.claude/rules/AGENT.md` — execution protocol and standards
3. `.claude/rules/CHANGELOG_RULE.md` — mandatory post-task changelog requirement
4. `.claude/skills/SKILLS.md` — skill index

After every task: update `CHANGELOG.md` per `.claude/rules/CHANGELOG_RULE.md`.

Skill index: `.claude/skills/SKILLS.md`
Agent index: `.claude/agents/AGENTS.md`

---

## Architecture Constraints

### Module boundaries

- Controllers MUST NOT access Repository beans directly — always through Service layer
- Cross-module access: import Service interface only, never Entity or Repository from another module
- `common/` packages are the only legitimate cross-cutting dependency
- Feature modules must not import from sibling feature modules

### Transaction rules

- `@Transactional` belongs on Service impl methods, not Controller or Repository
- Never annotate interface methods with `@Transactional`

### Testing rules

- Integration tests use Testcontainers — never H2 as a PostgreSQL substitute
- Target: 85% line coverage, 80% branch coverage on Service impl classes
- Test class naming: `{ClassName}Test` (unit), `{ClassName}IT` (integration)
- Full context load test: `ApplicationTests` — requires all infrastructure up

### Resilience rules

- Resilience4j is the declared resilience library (circuit breaker, rate limiter, retry)
- Three pre-configured rate limiter instances: `lowTraffic` (60 req/30 s), `mediumTraffic` (120 req/30 s), `highTraffic` (120 req/30 s)
- Default circuit breaker: sliding window 10, failure threshold 50 %, wait in open 10 s
- Default retry: 3 attempts, 1 s initial wait, exponential backoff (multiplier 2)
- Apply resilience annotations at the Service layer, not Controller or Repository

### Security rules

- All endpoints require JWT unless explicitly permitted in `SecurityConfig`
- JWT access + refresh token strategy; TTLs are environment-variable driven
- OAuth2 providers: declared in the `security` namespace of profile configs

### RabbitMQ rules

- RabbitMQ is the declared async message broker
- No exchanges or queues are defined yet; add configuration under `common/config/`
- Use AMQP for event-driven and async processing patterns

---

## Feature Modules

All modules reside under `src/main/java/com/app/modules/`. All are currently scaffolded (empty; implementation pending).

| Module | Responsibility |
|--------|----------------|
| `auth` | Authentication: login, register, OAuth2, JWT refresh, password reset, email verification |
| `users` | User profiles, notification/privacy settings, push tokens |
| `social` | Social graph: follow relationships (public/private), block relationships |
| `media` | Media asset management: upload metadata, CDN URL, blurhash, dimensions |
| `post` | Posts: creation, carousel media, likes, saves, user tags, location metadata |
| `comment` | Nested comments (adjacency list + root_id, depth ≤ 10): creation, likes, replies |
| `hashtag` | Hashtag management, post-hashtag associations, trending snapshots |
| `story` | 24-hour ephemeral stories: creation, view tracking |
| `notification` | In-app notifications for social events (likes, comments, follows, messages) |
| `message` | Direct messaging: 1-1 and group conversations, message threads |
| `report` | Content flagging: user reports on posts, comments, stories, messages, profiles |
| `admin` | Moderation: admin action audit log (ban, suspend, remove, restore) |
| `recommendation` | Feed ranking: user behavior events, post interaction scores, collaborative filtering |

---

## Code Formatting

Spotless 2.46.1 with Google Java Format (AOSP):
- Tab-based indentation (4 spaces per tab)
- Import order: `java`, `jakarta`, `org`, `com`
- Unused imports removed automatically
- Trailing whitespace trimmed, final newline enforced
- Format check runs at the `compile` phase (will block `./mvnw compile` on violations)

Run `./mvnw spotless:apply` before committing.
