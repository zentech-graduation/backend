# .workspace

Per-developer working directory for agent tasks. Content is not tracked by git — safe to write freely without affecting the project. Each developer maintains their own copy.

## Folder guide

| Folder | Purpose |
|--------|---------|
| `plans/` | Upcoming work not yet started — task breakdowns, implementation plans, branching strategies |
| `progress/` | Active session state — progress tracking files for ongoing or recent tasks |
| `reports/` | Completed outputs — scan results, PR descriptions, summaries, audit outputs |
| `scripts/` | Reusable bash or Python scripts intended for use across multiple sessions |
| `tmp/` | Scratch space — disposable intermediate files; do not rely on these persisting |

## First-time guidance for an agent arriving with no context

1. Read this file.
2. Scan `progress/` for any `*_progress.md` files — these describe active or recently interrupted tasks, including which steps are complete and which remain.
3. Scan `plans/` for pending work that has not yet started.

## Notes

- Agents may create subfolders freely within any of the five top-level folders. The structure above is the baseline minimum.
- Write file names and content as if explaining to a colleague who has no prior session context — other agents in future sessions will navigate this folder to understand what has been done.
- Prefer descriptive names: `{task-name}_progress.md`, `{task-name}_plan.md`, `{topic}_report.md`.
