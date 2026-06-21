---
trigger: model_decision
description: Load when executing any multi-step task that could be interrupted. Defines how to create and maintain a progress file so tasks are resumable across sessions.
---

# Skill: Progress Tracking

## When to use

Any task with more than three sequential steps that could be interrupted mid-execution. Not needed for simple single-shot tasks or tasks that complete in a single session with no meaningful interruption risk.

## File location and naming

```
.workspace/progress/{task-name}_progress.md
```

Use a descriptive task name that identifies the work. One file per task — never read a different task's progress file as if it were your own. If running multiple tasks in parallel, each task has its own progress file.

## Status values

| Value | Meaning |
|-------|---------|
| `PENDING` | Step has not started |
| `IN_PROGRESS` | Step is currently executing |
| `COMPLETE` | Step finished successfully |
| `FAILED` | Step finished with an error — do not retry without explicit instruction |

## Update protocol

This is the most important part. Violating these rules leaves the progress file in an inconsistent state.

1. Write `IN_PROGRESS` **before** starting a step.
2. Write `COMPLETE` or `FAILED` **immediately after** the step resolves — before starting the next step.
3. Never leave a step in `IN_PROGRESS` after it has resolved.
4. Never start the next step before writing the current step's result.

## Resume logic

The first action in every session that uses progress tracking is to read the progress file. Apply the following rules per step:

| State found | Action |
|-------------|--------|
| File not found | Create it fresh; set all steps to `PENDING` |
| `COMPLETE` | Skip — do not re-execute |
| `IN_PROGRESS` | Step was interrupted. Reset to `PENDING` and re-execute from scratch. Do not assume partial work from the prior session is valid. |
| `FAILED` | Halt immediately. Do not retry. Report the failure and wait for explicit instruction. |
| `PENDING` | Execute the step normally |

## Progress file template

```markdown
# Task: {task-name}
# Started: {date}

## Steps

### Step 1 — {description}
- Status: PENDING
- Result: —

### Step 2 — {description}
- Status: PENDING
- Result: —

### Step 3 — {description}
- Status: PENDING
- Result: —

## Notes
{Any context needed for resume — branch name, last file touched, error details, etc.}
```

## Critical anti-pattern

Do not confuse progress files from different tasks. If a session uses its own progress file and also reads another task's progress file for reference (for example, to extract a list of completed items), refer to them by their distinct filenames explicitly. Never read the wrong file and treat it as your own task's state.

## Checklist

- [ ] Progress file created at `.workspace/progress/{task-name}_progress.md`
- [ ] All steps initialized to `PENDING` at session start
- [ ] `IN_PROGRESS` written before each step begins
- [ ] `COMPLETE` or `FAILED` written immediately after each step resolves
- [ ] On resume: `IN_PROGRESS` steps reset to `PENDING` and re-executed
- [ ] `FAILED` steps cause an immediate halt with no retry
- [ ] Progress file not confused with another task's progress file
