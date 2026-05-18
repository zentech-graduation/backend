---
trigger: model_decision
description: Load when creating branches, writing commit messages, or opening pull requests. Contains branch naming, commit format, and PR size rules.
---

# Git Workflow

develop: Development branch (The main programming activities will be pushed here. Changes, updates, additions, and modifications will all be pushed here.)
main: Production branch (This is the terminal branch, accessible only by the owner.)

## Branch naming

Format: `<type>/<scope>/<short-description>`

| Segment | Allowed values |
|---------|----------------|
| `type` | `feat`, `fix`, `chore` |
| `scope` | any valid scope from the commit scope list below |
| `short-description` | lowercase, hyphen-separated words |

```
feat/post/add-carousel-support
fix/auth/refresh-token-expiry
chore/db/add-story-indexes
```

## Commit message format

Format: `<type>(<scope>): <subject>`

Rules enforced by `pr-lint` workflow:
- Subject must start with a lowercase letter (`^[a-z].+$`)
- Scope is **required** (`requireScope: true`)

### Allowed types

| Type | When to use |
|------|-------------|
| `feat` | New feature |
| `fix` | Bug fix |
| `refactor` | No-behavior-change code change |
| `test` | Adding or updating tests |
| `chore` | Build, tooling, dependency updates |
| `docs` | Documentation only |
| `perf` | Performance improvement |
| `ci` | CI/CD configuration |


### Allowed scopes (pr-lint enforced)

`auth` · `mail` · `users` · `social` · `media` · `post` · `comment` · `hashtag` · `story` · `notification` · `message` · `report` · `admin` · `recommendation` · `common` · `db` · `ci`

> **Discrepancies found in git history** — the following scopes appear in existing commits but are **not** in the pr-lint allowlist and will fail CI if used:
> `config`, `security`, `environment`, `changelog`, `database` (use `db`), `modules`, `build`, `log`
> One commit also omitted scope entirely (`docs: add CONTRIBUTING…`) — this violates `requireScope: true`.

```
feat(post): add carousel media support
fix(auth): prevent concurrent refresh token consumption
chore(db): add GIN index on hashtag_name for trigram search
refactor(common): extract token blacklist TTL calculation
```

Note: Only use short description

## Pull request rules

### Title format
Same as commit message format — enforced by `pr-lint` workflow on PR open, edit, sync, and reopen.

### Size labels (pr-size workflow)

| Label | Changed lines |
|-------|---------------|
| `size/XS` | ≤ 100 |
| `size/S` | 101–400 |
| `size/M` | 401–1000 |
| `size/L` | 1001–2000 |
| `size/XL` | > 2000 |

`fail_if_xl: false` — XL PRs are **labelled and warned, not blocked**. CONTRIBUTING.md states a 1000-line block; this is incorrect — no workflow actually fails on size.

Split large PRs proactively: keep feature PRs under `size/M` (≤ 1000 lines) as a target.

### Process checklist
- [ ] Branch follows `<type>/<scope>/<description>` naming
- [ ] All commits follow `<type>(<scope>): <subject>` with an allowlisted scope
- [ ] `./mvnw spotless:apply` and `./mvnw test` both pass
- [ ] PR template author checklist completed
- [ ] PR title matches commit format (pr-lint will block merge otherwise)
- [ ] At least one CODEOWNERS-assigned reviewer has approved

## Pre-commit checklist
- [ ] Commit type is one of the 8 allowed types
- [ ] Scope is from the pr-lint allowlist (do not use `config`, `database`, `build`, etc.)
- [ ] Subject starts with a lowercase letter
- [ ] `./mvnw spotless:check` passes
- [ ] CHANGELOG.md updated

## Quick reference

| Type | When |
|------|------|
| `feat` | New endpoint, new module feature |
| `fix` | Bug fix, incorrect behavior corrected |
| `refactor` | Internal restructure, no behavior change |
| `test` | Test added or updated |
| `chore` | Build, deps, tooling, non-code config |
| `docs` | README, DATA_RULES, Javadoc-only |
| `perf` | Measurable performance improvement |
| `ci` | GitHub Actions, workflow changes |
