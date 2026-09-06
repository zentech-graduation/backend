---
trigger: model_decision
description: Load when starting any task that involves writing intermediate files, plans, reports, or scripts. Defines how to use .workspace/ as the agent's working directory.
---

# Skill: Workspace Usage

## What it is

`.workspace/` is a per-developer directory at project root for agent-generated outputs — plans, reports, scripts, progress files, and scratch work. It is not tracked by git, so writing to it freely does not affect the project or other developers. Each developer maintains their own copy.

## Folder guide

| Folder | Contents |
|--------|---------|
| `plans/` | Work not yet started — task breakdowns, implementation plans, branching strategies |
| `progress/` | Active session state — progress tracking files for ongoing or recent tasks |
| `reports/` | Completed outputs — scan results, PR descriptions, summaries, audit outputs |
| `scripts/` | Reusable bash or Python scripts. Store here when a script will be needed across multiple sessions |
| `tmp/` | Scratch space — disposable intermediate files. Do not rely on these persisting |

## First action when arriving with no context

1. Read `.workspace/README.md`.
2. Scan `progress/` for any `*_progress.md` files to find active or recently interrupted tasks.
3. Scan `plans/` for pending work that has not yet started.

## Naming conventions

Use descriptive names that identify the task and, optionally, a date:

- `{task-name}_progress.md` — progress tracking for an active task
- `{task-name}_plan.md` — implementation plan for upcoming work
- `{topic}_report.md` — completed scan, audit, or summary output

## Agent flexibility

Agents may create subfolders within any of the five top-level folders. The baseline structure is a minimum — extend it freely for the task at hand. For example, `reports/auth/` or `scripts/db/` are fine.

## Multi-session continuity

Other agents in future sessions, potentially with no prior context, can navigate this folder to understand what has been done. Write file names and content as if explaining to a colleague: clear, self-contained, no assumed context from the current session.

## Checklist

- [ ] Reading `.workspace/README.md` and `progress/` before starting if arriving without context
- [ ] Plans written to `plans/`, not to the project root or `tmp/`
- [ ] Active session state (progress files) written to `progress/`
- [ ] Completed outputs (scan results, PR text, summaries) written to `reports/`
- [ ] Reusable scripts written to `scripts/`
- [ ] Scratch work and throwaway files written to `tmp/`
- [ ] File names are descriptive and self-explanatory to a future agent with no context
