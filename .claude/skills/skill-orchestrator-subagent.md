---
trigger: model_decision
description: Load when planning a multi-step implementation session that benefits from isolated execution context per step. Defines the orchestrator + sub-agent pattern: role boundaries, Task prompt requirements, retry policy, and progress integration.
---

# Skill: Orchestrator + Sub-Agent Pattern

## When to use

- Multiple fixes or changes need isolated execution context — one failure must not contaminate the next step.
- The concerns of "making a change" and "verifying + committing a change" are better separated.
- A step involves writing source or config changes that may fail verification, requiring a clean retry.

## When NOT to use

- Simple single-step tasks.
- Tasks where the sub-agent needs state the orchestrator has built up in memory — share that context directly instead of using the pattern.

## Role definitions

Enforce these boundaries strictly. Do not blur them.

### Orchestrator

- Coordinates the session end-to-end.
- Manages branches: creates, checks out, and tracks branch state.
- Runs `./mvnw spotless:apply` before running tests whenever Java source files have changed.
- Runs `./mvnw test` and interprets results.
- Commits changes after verification passes.
- Updates the progress file before and after each step.
- Performs verification and decides: continue, retry, or halt.
- **Does not write production code changes directly.**

### Sub-agent (Task tool)

- Makes source code or config changes only.
- Does not run build commands.
- Does not run tests.
- Does not commit.
- Does not push.
- Does not update the progress file.
- Returns the change and stops.

## Task prompt requirements

Every Task prompt must be self-contained. The sub-agent starts with no orchestrator context — it only knows what the Task prompt provides. Include all of the following:

1. **Hard constraints** — listed verbatim at the top of the prompt.
2. **Specific files to read** — full paths; the sub-agent must not guess.
3. **The specific change to make** — precise, unambiguous.
4. **What to output** — describe the expected return value (a diff summary, a list of modified files, etc.).

### Hard constraints to include in every Task prompt

```
Hard constraints:
- No git commit, git push, git add, git stash, git merge, git rebase, git cherry-pick, git restore --staged, git clean
- No ./mvnw, no test execution, no build commands
- No progress file updates
- Make only the changes described. Return a summary of what was changed and stop.
```

## Retry and halt policy

1. After the sub-agent returns, the orchestrator runs verification (spotless, tests, or both as appropriate).
2. **If verification fails**: retry the Task once with the same prompt.
3. **If it fails again**: mark the step `FAILED` in the progress file, halt the pipeline. Do not skip a failed step to proceed to the next.
4. **Never silently ignore a failure.** A failed step that is skipped means subsequent steps build on broken state.

## Integration with progress tracking

See `skill-progress-tracking` for the full protocol. The orchestrator:

- Writes `IN_PROGRESS` to the step in the progress file **before** spawning the Task.
- Writes `COMPLETE` or `FAILED` **after** the result and verification are both complete.

## Forbidden operations for sub-agents

Include this list verbatim in every Task prompt. The sub-agent must not execute:

```
git commit
git push
git stash
git add
git merge
git rebase
git cherry-pick
git restore --staged
git clean
./mvnw (any Maven goal)
any test runner
any build command
```

## Checklist

- [ ] Orchestrator role: coordinates, verifies, commits, updates progress
- [ ] Sub-agent role: makes code/config changes only, returns result, stops
- [ ] Every Task prompt is self-contained with full file paths
- [ ] Hard constraints listed at top of every Task prompt
- [ ] Forbidden operations explicitly listed in every Task prompt
- [ ] Progress file updated to `IN_PROGRESS` before spawning Task
- [ ] Progress file updated to `COMPLETE` or `FAILED` after verification
- [ ] Verification failure → one retry → on second failure: `FAILED` + halt
- [ ] No failed step silently skipped
