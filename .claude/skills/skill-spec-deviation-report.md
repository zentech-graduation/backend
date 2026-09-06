---
trigger: model_decision
description: Load during implementation whenever a task specification or plan conflicts with the actual codebase — architecture pattern, existing convention, or technical constraint. Defines the stop-and-report protocol and the mandatory deviation report format.
---

# Skill: Spec Deviation Report

## When to use

Mid-implementation, when what the spec or plan asks for does not match what the codebase actually does or allows — a conflicting architecture pattern, a convention the spec didn't account for, or a technical constraint (schema, library version, transaction boundary) the spec assumed away.

## The core rule

On discovering a deviation, stop forward implementation on the **affected component** immediately. Do not silently substitute your own architectural judgment and keep going as if the conflict were resolved. A deviation that gets silently resolved by the agent's own judgment removes the task owner's ability to choose between tradeoffs they may care about — even when the agent's judgment is technically sound.

This does not mean halting the entire task. Work on unaffected components can continue; only the specific component in conflict stops.

## Mandatory reporting format

Every deviation gets its own block, using this exact structure:

```
## DEVIATION: <short id, e.g. D1>
**Spec says**: <exact quote or precise paraphrase of the spec requirement>
**Codebase reality**: <what was found, with file/line citation>
**Why they conflict**: <technical reason>
**Options**:
  1. <option> — tradeoff: <...>
  2. <option> — tradeoff: <...>
**Recommendation**: <agent's recommended option and why>
```

"Codebase reality" requires the same file/line rigor as `skill-endpoint-verification.md` — a claim about what the code does is only as good as its citation.

## When the agent may proceed automatically

Only when the deviation is a **pure implementation detail with zero behavioral, architectural, or scope impact** — e.g., a helper method name collision resolved by renaming, a formatting convention mismatch. In that case, note the deviation in the batch report (see below) with `Recommendation: applied automatically — no behavioral impact`, and continue.

Anything touching data flow, module boundaries, event architecture, transaction boundaries, denormalized counter ownership, or the original request's intent requires explicit approval before implementation resumes on that component. When in doubt about which side of this line a deviation falls on, treat it as requiring approval — the cost of an unnecessary pause is far lower than the cost of an unapproved architectural change.

## Batching

Collect all deviation reports for a single task into one section of the final report. Do not scatter them inline through the middle of unrelated implementation narrative — the reviewing party needs to approve or reject the full set as a batch, and that is only possible if the full set is visible in one place.

## Output contract

- Every deviation reported in the exact `## DEVIATION: <id>` format above, no exceptions for "minor" ones
- Implementation halted on the affected component until either (a) the deviation is a zero-impact implementation detail resolved automatically and logged, or (b) explicit approval is given
- All deviations for a task collected into one section, not scattered inline
- "Codebase reality" claims carry file/line citations

## Checklist

- [ ] Deviation discovered mid-implementation → forward work on the affected component stopped immediately
- [ ] Deviation written in the exact `## DEVIATION: <id>` format with all five fields present
- [ ] "Codebase reality" cites file/line, not a general impression
- [ ] At least two options listed with tradeoffs, plus a recommendation
- [ ] Auto-proceed only used for deviations with zero behavioral/architectural/scope impact
- [ ] All deviations for the task collected into one batched section of the final report
- [ ] No deviation silently resolved by agent judgment when it touches data flow, module boundaries, event architecture, or the original request's intent
