---
trigger: model_decision
description: Load any time you need the user or reviewer to approve a scope, plan subset, or severity classification. Prohibits presenting a condensed choice menu without first showing the full underlying content it was built from.
---

# Skill: Scope Menu Full Disclosure

## When to use

Any time an approval decision is being requested for a scope, a subset of a plan, or a severity/priority classification — most commonly after an audit, a multi-item plan, or a review that produced more findings or rows than fit comfortably in a short message.

## The core rule

Do not present a condensed multi-choice menu ("Option 1 / Option 2 / Option 3") as the basis for approving scope drawn from a longer audit, plan, or analysis document, unless the full underlying content has already been shown. A menu is a compression of a document. Compressing before disclosure means the reviewer approves a summary of your summary — they never see the rows the categories were built from, and cannot catch a miscategorization or a dropped item.

## Sequencing requirement

1. Output the verbatim relevant rows or sections from the source document first — the audit findings table, the severity table, the plan's row-by-row breakdown. Verbatim means the actual rows, not a restatement of them in different words.
2. Only after that full disclosure, offer the choice menu if one is useful for the reviewer's convenience.

Skipping step 1 and going straight to step 2 is the violation this skill exists to prevent.

## Severity and priority labels must be traceable

Any label the agent assigns — Critical/High/Medium/Low, P0/P1/P2, "must fix" vs. "nice to have" — must be traceable to the specific evidence line that justifies it. The label alone, without the evidence line next to it, is not sufficient disclosure. A reviewer who sees "3 Critical findings" and nothing else has no way to judge whether the agent's Critical bar matches their own; a reviewer who sees "Critical: raw JWT secret logged at `AuthServiceImpl.java:212`" can judge it immediately.

## Menu construction after disclosure

If a menu is offered after full disclosure, each option must state precisely which rows or items it includes and excludes — not a vague category description. "Option A: fix the security issues" is insufficient. "Option A: fixes findings F1, F3, F5 (the three findings tagged Critical above); leaves F2, F4, F6 for a follow-up" is sufficient, because the reviewer can check it against the disclosed rows without asking a follow-up question.

## Output contract

- Full verbatim source rows/sections shown before any condensed menu
- Every severity/priority label paired with the evidence line that justifies it
- Every menu option lists exactly which disclosed items it includes and excludes
- No approval request built on a summary the reviewer has not seen the source of

## Checklist

- [ ] Verbatim source rows/sections disclosed before any menu is offered
- [ ] Every severity/priority label has an adjacent evidence citation
- [ ] Menu options (if offered) state included/excluded items precisely, not by vague category
- [ ] No approval requested on compressed content the reviewer hasn't seen in full
