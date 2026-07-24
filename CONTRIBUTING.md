# Contributing

## Ways to contribute

- **Bug reports** — open a bug report issue using the provided template.
- **Feature requests** — open a feature request issue using the provided template.
- **Code** — implement features or fixes via a pull request.
- **Documentation** — improve `README.md`, module `DATA_RULES.md` files, or inline Javadoc.
- **Tests** — add unit or integration tests to increase coverage.

## Development setup

**Prerequisites**

- Java 21
- Maven (or use the included `./mvnw` wrapper)
- Docker (required for PostgreSQL, Redis, and RabbitMQ)

**Start the local environment**

```bash
docker compose up -d
./mvnw spring-boot:run
```

The application starts with the `dev` profile. Swagger UI is available at `http://localhost:8080/swagger-ui`.

### Troubleshooting: startup fails with a Flyway validation error mentioning version 99

Flyway used to apply a dev-only seed script, `V99__seed_feed_test_data.sql`, from `classpath:db/dev-seed`.
That script and its location entry have been removed.
If your local database already recorded that migration as applied, Flyway's validation step will now find a schema history row with no matching migration file and refuse to start the application.

Fix it by removing that one row from your local database, then start the application again:

```bash
docker compose exec postgres psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "DELETE FROM flyway_schema_history WHERE version = '99';"
```

The seed script only ever inserted disposable test rows, so no other cleanup is required.
If you would rather start from a clean database, remove the `postgres` container instead — it has no named volume, so removing it discards its data and Flyway reapplies every migration from scratch on the next `docker compose up -d`:

```bash
docker compose rm -f -s postgres
docker compose up -d postgres
```

## Branch naming convention

```
<type>/<scope>/<short-description>
```

| Segment | Values |
|---------|--------|
| type | `feat`, `fix`, `chore` |
| scope | module name (`auth`, `post`, `social`, …) or `common`, `db`, `ci` |
| short-description | lowercase, hyphen-separated words |

Examples:

```
feat/post/add-carousel-support
fix/auth/refresh-token-expiry
chore/db/add-story-indexes
```

## Commit message standard

Follow [Conventional Commits](https://www.conventionalcommits.org/):

```
<type>(<scope>): <description>
```

| Type | When to use |
|------|-------------|
| `feat` | New feature |
| `fix` | Bug fix |
| `refactor` | Code change with no behavior change |
| `test` | Adding or updating tests |
| `chore` | Build, tooling, dependency updates |
| `docs` | Documentation only |
| `perf` | Performance improvement |
| `ci` | CI/CD configuration |

Scope must be a module name or `common`, `db`, `ci`. PR titles are linted by the `pr-lint` workflow and must conform to this format.

Examples:

```
feat(post): add carousel media support
fix(auth): prevent concurrent refresh token consumption
chore(db): add GIN index on hashtag_name for trigram search
refactor(common): extract token blacklist TTL calculation
```

## Pull request process

1. Create a branch following the naming convention above.
2. Implement the change with tests.
3. Run `./mvnw spotless:apply && ./mvnw test` and confirm both pass.
4. Complete every item in the author checklist in `.github/pull_request_template.md`.
5. Open a PR against `main`. The PR title must conform to Conventional Commits — the `pr-lint` workflow enforces this.
6. CODEOWNERS assigns reviewers automatically based on the files changed. At least one assigned reviewer must approve before merge.
7. PRs larger than 1000 changed lines are blocked by the `pr-size` workflow. Split them.

## Code style

Google Java Format (AOSP variant) enforced by Spotless. Before committing:

```bash
./mvnw spotless:apply
```

Import order: `java`, `jakarta`, `org`, `com`. Tab indent: 4 spaces. The pre-commit hook (`hooks/pre-commit-lint.sh`) blocks commits that violate comment style rules.

## Project-specific rules

These rules differ from generic Java conventions and are strictly enforced during review:

1. **`@Transactional` on Service impl methods only.** Never place it on interfaces or Controller classes.
2. **Denormalized counters are trigger-maintained.** Never write directly to `follower_count`, `following_count`, `post_count`, `like_count`, `comment_count`, `save_count`, `view_count`, or `reply_count` from application code.
3. **All responses must use `ApiResponse<T>`.** Paginated responses use `PageResponse<T>` (offset) or `CursorPageResponse<T>` (cursor).
4. **Controllers delegate to Service only.** No business logic, no repository calls, no direct entity manipulation in the Controller layer.
5. **Comments in English only.** No other language in source files.

## Running tests

Docker must be running — Testcontainers starts PostgreSQL automatically for integration tests.

```bash
./mvnw test
```

## Reporting security issues

See [SECURITY.md](SECURITY.md). Do not open a public issue for security vulnerabilities.
