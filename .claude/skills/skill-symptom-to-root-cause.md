---
trigger: model_decision
description: Load before starting any bug investigation, audit, or fix-planning task that begins from a user-reported symptom. Requires a clarification checklist and a symptom-confirmation checkpoint before any fix plan is written.
---

# Skill: Symptom to Root Cause

## When to use

Any task that starts from a symptom description rather than a known, located defect — "the server crashes sometimes," "logs are leaking to the frontend," "auth is broken," "feed is slow for some users."

## The core rule

A vague or ambiguous symptom description is not sufficient basis for scoping an audit or a fix plan. Scoping against an unverified symptom means the search area is guessed, not derived — and a guessed search area routinely finds *a* plausible-looking defect nearby without ever confirming it is *the* defect the reporter actually experienced.

## Mandatory clarification checklist

Before scanning any code, run through this list and explicitly ask the requester for whichever items are missing:

- **Exact error message or log line**, verbatim, if one exists. A paraphrase of an error message is not the error message.
- **Stack trace**, if the symptom involves a crash or an exception.
- **Reproduction steps or conditions** — which endpoint was hit, the shape of the request payload, load conditions, and frequency. "Sometimes" is not a frequency; ask for a rough rate, a time window, or a specific instance if one is known.
- **Environment where observed** — dev, prod, local, or a specific service instance. A dev-only symptom and a prod-only symptom can have entirely different root causes even with an identical description.
- **Evidence basis** — is the symptom confirmed from direct evidence (logs, monitoring dashboards, screenshots), or is it the requester's inference or guess about what's happening? These get very different treatment: direct evidence narrows the search; an inference is itself a hypothesis to be tested, not a fact to build a plan on.

Do not proceed past this checklist by filling in gaps with assumptions. If the requester cannot supply an item, note it as genuinely unknown and factor that into the investigation plan (e.g., "no stack trace available — first step is reproducing it to capture one") rather than silently substituting a guess.

## Handling requester-stated uncertainty

If the requester states uncertainty about their own symptom description — "might be X or might be Y," "not sure if it's the cache or the DB" — treat this as **two independent hypotheses requiring separate verification**, not as a single ambiguous symptom to silently resolve by picking the more likely-sounding one. Investigate both, or state explicitly which one is being investigated first and why, with the other queued as the fallback if the first doesn't confirm.

## Symptom confirmation checkpoint

Root-cause scanning driven by an unverified symptom description must produce a **symptom confirmation checkpoint** before any fix plan is written. This checkpoint confirms:

1. The actual defect exists in the code (with file/line citation, per `skill-endpoint-verification.md`'s citation discipline).
2. The defect's observable behavior matches the reported symptom — not merely that a plausible-looking defect was found somewhere nearby.

Finding *a* bug is not the same as finding *the* bug the reporter experienced. A fix plan built on a defect that merely resembles the symptom, without a confirmed causal link, risks shipping a fix that doesn't resolve the actual complaint while leaving the real cause untouched.

## Output contract

- Clarification checklist run and any missing items either obtained from the requester or explicitly logged as unknown
- Requester-stated uncertainty split into separate, individually verified hypotheses — never silently collapsed into one guess
- A symptom confirmation checkpoint, with file/line citation, precedes any fix plan
- The confirmed defect's behavior is shown to match the reported symptom, not just to exist nearby

## Checklist

- [ ] Exact error message/log line obtained verbatim, or explicitly logged as unavailable
- [ ] Stack trace obtained (if a crash/exception is involved), or explicitly logged as unavailable
- [ ] Reproduction steps/conditions obtained, with a real frequency indicator (not "sometimes")
- [ ] Environment where observed identified
- [ ] Evidence basis (direct evidence vs. requester inference) identified
- [ ] Requester-stated uncertainty treated as multiple hypotheses, each verified separately
- [ ] Symptom confirmation checkpoint completed, with citation, before any fix plan is written
- [ ] Confirmed defect's behavior matches the reported symptom, not just proximity to it
