---
trigger: model_decision
description: Load any time you notice a defect, rule violation, or risk while working on a defined task that is not part of that task's approved scope. Defines the mandatory reporting section and cites the recorded PII-in-logs precedent (ResendMailSender.java, fixed in commit 03d2868) as the canonical example.
---

# Skill: Out-of-Scope Finding

## When to use

Mid-task, whenever something is noticed that is real and worth acting on but was not part of the task the agent was asked to do — a security issue, a PII exposure, a correctness bug, a performance problem, a style violation, or dead code, discovered incidentally while working on something else.

## The core rule

Out-of-scope findings must never be silently fixed, and must never be silently ignored. Silently fixing expands the diff beyond what the task owner approved and can hide an unrelated behavioral change inside a reviewable-looking PR. Silently ignoring means a known defect goes unrecorded and is rediscovered later at higher cost, or never rediscovered at all.

## Mandatory reporting section

Append this section to the end of any task report, every time, whether or not anything was found:

```
## OUT-OF-SCOPE FINDINGS
### Finding: <short description>
**Location**: <file/line>
**Category**: <security | PII | correctness | performance | style | dead code | other>
**Severity (agent's assessment)**: <...>
**Why this is out of scope**: <...>
**Suggested handling**: <fix now in a separate commit | defer to follow-up | needs owner decision>
```

If nothing was found, the section is still present, stating explicitly:

```
## OUT-OF-SCOPE FINDINGS
None found.
```

An absent section is indistinguishable from "the agent didn't look." An explicit "None found" is the only way to signal that the check ran.

## Worked example — the recorded precedent in this project

While consuming or sending mail (an unrelated logging-improvement task), a prior session found that `ResendMailSender.java`'s private `send(...)` method logged the raw recipient email address in both the success and failure log lines:

```java
log.info("Email sent to {} | subject: {}", toEmail, subject);
...
log.error("Failed to send email | recipient: {} | subject: {} | error: {}", toEmail, subject, e.getMessage());
```

This violates a "no PII in logs" expectation that was not part of that session's originally scoped task. It was reported as an out-of-scope finding and fixed in a separate commit, `03d2868 fix(mail): remove recipient email address from send success/failure logs`. The current file (`src/main/java/com/app/modules/mail/service/impl/ResendMailSender.java:108` and `:110`) logs only the `subject`, not `toEmail` — the fix is already applied. Use this as the canonical example of the pattern: *found incidentally → reported via the format above → fixed in its own commit, not bundled into the task that surfaced it.*

Do not assume this example describes the current state of every file forever — verify current code before citing a finding as still-open. The point of the example is the reporting-and-handling pattern, not a permanently frozen claim about `ResendMailSender.java`.

## Commit discipline

Never bundle an out-of-scope fix into the same commit as in-scope work, even when the fix is one line and obviously correct. This follows the commit granularity policy in `git_workflow.md`: a commit is one logical, self-contained change, and "found while doing X, fixed while doing X" is exactly the mega-commit pattern that policy forbids. An out-of-scope fix that the task owner approves gets its own commit with its own `<type>(<scope>): <subject>` message describing the fix itself, not the task that surfaced it.

## Output contract

- `## OUT-OF-SCOPE FINDINGS` section present at the end of every task report, with no exceptions
- Every finding uses the exact five-field format above
- "None found." stated explicitly when nothing was found
- No out-of-scope fix applied without the finding being reported first
- No out-of-scope fix bundled into the same commit as in-scope work

## Checklist

- [ ] `## OUT-OF-SCOPE FINDINGS` section present in the task report
- [ ] Every finding has Location, Category, Severity, Why-out-of-scope, and Suggested handling
- [ ] "None found." stated explicitly if the check ran and found nothing
- [ ] No finding silently fixed without being reported first
- [ ] No finding silently dropped/ignored
- [ ] Any approved out-of-scope fix lands in its own commit, separate from in-scope work
