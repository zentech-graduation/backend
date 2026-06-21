---
trigger: always_on
description: Always active. Establishes .workspace/ as the agent working directory and defines the required first-action checklist for every session.
---

# Workspace Rule

## What is `.workspace/`

`.workspace/` exists at the project root. It is the agent's working directory for every task — plans, reports, scripts, progress files, and scratch work all go here. Content is not tracked by git; each developer has their own copy. Writing here does not affect the project or other developers.

## First action — every session, every task

1. Read `.workspace/README.md`.
2. Check `.workspace/progress/` for any `*_progress.md` files. If one exists for the current task, read it to determine what is complete and what is pending before doing anything else.

## All output goes to `.workspace/`

Do not write plans, reports, scripts, or intermediate files to the project root or to any project-owned directory (`src/`, `docs/`, `database/`, etc.) unless the file genuinely belongs to the project — source code, Flyway migrations, or official documentation.

## Default subdirectories

| Folder | Contents |
|--------|---------|
| `plans/` | Upcoming work not yet started |
| `progress/` | Active session state — progress tracking files |
| `reports/` | Completed outputs — scans, summaries, PR text |
| `scripts/` | Reusable bash or Python scripts |
| `tmp/` | Scratch work; do not rely on these persisting |

Agents may create additional subfolders within any of the above as needed.

## Skill reference

Load `skill-workspace` for the full file-naming conventions, multi-session continuity rules, and guidance on organizing output within `.workspace/`.
