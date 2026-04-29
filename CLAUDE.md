<!--
  Agentic Software Team — agentic-team-antigravity
  Copyright (c) 2026 Dung Le Hoang (Tumi) — https://lehoangdung.blog
  Personal proprietary work. All rights reserved.
-->

# CLAUDE.md — Agentic Software Team Project Rules

> Read by the Antigravity agent at every session start.
> This is the project constitution. All workspace skills and workflows enforce these rules.

---

## Project context

- **Project name**: app
- **Repository**: git@github.com:Nuvorah/app.git
- **Type**:  Greenfield

---

## How this team works in Antigravity

This project uses a structured multi-role agentic team. Each role is a **Skill** in
`.agent/skills/` — loaded on demand when you describe what you need.
Workflows (triggered with `/`) orchestrate the full SDLC.

### Invoking roles

| Role | How to invoke |
|---|---|
| Business Analyst | "Analyze requirements for {brief}" or `/analyze-requirements` |
| Project Manager | "Plan sprint from approved specs" or `/plan-sprint` |
| Tech Lead | "Review PR for TASK-00001" or `/techlead-review TASK-00001` |
| Developer | "Implement TASK-00001" or `/implement-task TASK-00001` |
| Tester | "Run tests for TASK-00001" or `/run-tests TASK-00001` |
| Sprint QA | "Audit sprint compliance" or `/sprint-qa SPRINT-01` |
| Doc Sync | "Sync docs after sprint" or `/sync-docs sprint 1 complete` |

---


## Current user identity — resolved at runtime every session

**Never write "PO", "PO (human)", "Product Owner", or leave blank.**
Always resolve the actual GitHub identity of whoever is running this session.

Every agent that needs to attribute a decision, approval, or sign-off runs this
**at the moment of writing** — not once at setup, not cached between sessions:

```bash
CURRENT_LOGIN=$(gh api user --jq '.login' 2>/dev/null)
CURRENT_NAME=$(gh api user --jq '.name'  2>/dev/null)
[ -z "$CURRENT_NAME" ]  && CURRENT_NAME=$(git config user.name 2>/dev/null)
[ -z "$CURRENT_LOGIN" ] && CURRENT_LOGIN=$(git config user.email 2>/dev/null)
if [ -z "$CURRENT_NAME" ] && [ -z "$CURRENT_LOGIN" ]; then
  echo "Cannot resolve identity. What is your name or GitHub username?"
  # Use the reply
fi
CURRENT_USER="${CURRENT_NAME} (@${CURRENT_LOGIN})"
```

Use `$CURRENT_USER` everywhere a human name is written:
- `**Decision:** {choice} — {CURRENT_USER} — {YYYY-MM-DD HH:MM}`
- `signed_off_by: "{CURRENT_USER}"`
- `**Resolved by:** {CURRENT_USER} — {YYYY-MM-DD HH:MM}`
- `PO approval: {CURRENT_USER} — {date}`
- `spec_approved_by: "{CURRENT_USER}"`

This means different people can approve specs, sign off smoke tests, and resolve
open questions in the same project — each action attributed to the right person.

## Hard rules — always enforced

- No direct commits to `main` or `develop` — always via feature branch + PR
- Every new function must have a unit test in the same PR
- No secrets or credentials in code — use environment variables
- All external API calls must have timeout and retry
- Conventional commits: `feat|fix|test|refactor|chore(scope): description`
- Minimum 80% coverage on new code, never regress existing
- Planning artifacts committed to `develop` before implementation starts

## Process-first rule

When a user pastes a screenshot, log, or error without invoking a workflow:
1. Describe what you see in one sentence
2. Ask: "Shall I file this as a bug report via `/report-bug`?"
3. Wait for response — do not investigate or fix anything

## Canonical artifact locations

| Artifact | Path |
|---|---|
| PR review | `.agent/workspace/tasks/TASK-{N}-review.md` |
| QA summary | `.agent/workspace/tasks/TASK-{N}-qa-summary.md` |
| Bug report | `.agent/workspace/qa-reports/bugs/BUG-{N}-{title}.md` |
| Sprint compliance | `.agent/workspace/qa-reports/SPRINT-{N}-compliance.md` |
| Smoke test report | `.agent/workspace/qa-reports/SMOKE-{N}-{version}.md` |
| SAD | `.agent/workspace/docs/SAD.md` |
| User Guide | `.agent/workspace/docs/USER-GUIDE.md` |
| ADR | `.agent/workspace/architecture/ADR-{N}.md` |

---

## Branch strategy

```
main        ← production — /release only
  └── develop   ← integration — planning commits + squash merges
        ├── feature/TASK-{N}-{title}
        ├── bugfix/TASK-{N}-{title}
        └── hotfix/BUG-{N}-{title}   ← branches from main only
```

## Task status lifecycle

```
pending → in_progress → review_ready → qa_passed → pr_open → merged
                                            ↓
                                         failed → bug filed
                                         blocked → Tech Lead resolves
```

## Dependency policy

Downgrades are always blocked. Upgrades require PO decision via options A/B/C.
Core language version changes require Tech Lead ADR + PO approval.

## Migration default

If PO has not requested a UI change, migrated UI must be identical to source.
Every difference is a bug until proven otherwise.

## CI health policy

| State | `/smoke-test` | `/release` |
|---|---|---|
| HEALTHY / FLAKY / RECOVERING_STABLE | ✓ proceed | ✓ proceed |
| RECOVERING_WATCH | ⚠ PO confirms | ⚠ PO confirms |
| JUST_FIXED (1 pass after 4+ failures) | ✗ blocked | ✗ blocked |
| BROKEN | ✗ blocked | ✗ blocked |
