---
trigger: model_decision
description: Load before planning any module implementation, new endpoint, or cross-module event integration. Defines the mandatory file-enumeration and verbatim-reporting protocol that must precede any implementation plan.
---

# Skill: Dependency Scan

## When to use

Before producing any implementation plan for:

- A new module implementation (scaffolded module being built out).
- A new endpoint on an already-implemented module.
- A cross-module event or integration (outbox/inbox wiring, RabbitMQ binding, shared DTO contract).

This skill governs what must be read before planning starts — it does not itself implement anything. `DOC_FIRST.md` establishes the reading-order requirement; this skill defines the exact file set per change type and the reporting format that proves the reading happened.

## Input required

- The change type: new module, new endpoint on an existing module, or cross-module event/integration.
- The module(s) involved.

## Steps

1. Classify the task into exactly one of the three change types below. If a task spans more than one type (e.g., a new endpoint that also emits a cross-module event), run the scan for each applicable type and merge the file lists — do not drop either set.

2. **New module** — enumerate and read:
   - `docs/modules/{module}/DATA_RULES.md`
   - `docs/modules/GLOBAL_RULES.md`
   - `.agents/rules/STRUCT.md`
   - `entity/`, `service/`, `repository/` from at least one already-implemented sibling module, used as pattern reference (not as a source of business rules for the new module)

3. **New endpoint on an existing module** — enumerate and read:
   - The module's own `controller/` package
   - The module's own `service/` interface
   - The module's own `dto/` package
   - The section of `common/ApiConstants.java` covering that module

4. **Cross-module event/integration** — enumerate and read:
   - The interfaces (not just entities) of every module being integrated with — a service interface defines the contract; an entity does not
   - `common/outbox/`
   - `common/inbox/`
   - `RabbitMqTopologyConfig.java`
   - `RabbitMqPublisherConfig.java`

5. Produce the scan report before writing the plan. The report is **verbatim**: full file contents reproduced exactly as they exist on disk, not summarized, not paraphrased, not condensed to "relevant excerpts." Structure it as:

   ```
   ## FILE: <relative path>
   <full file contents>

   ## FILE: <relative path>
   <full file contents>
   ```

   A summary of a file is not a substitute for the file. If a file is long, reproduce it in full anyway — the point of the scan is that nothing gets lost to paraphrase before the plan is built on top of it.

6. If a required file does not exist, do not silently treat the corresponding rule or data as inapplicable. State explicitly:

   ```
   MISSING: <path>
   ```

   This cross-references `DOC_FIRST.md` Section 3 ("Missing Documentation"): the correct response to a missing file is an explicit statement, never an invented substitute and never silent continuation as if the constraint does not exist.

7. Treat the dependency scan report as the only acceptable input for the downstream plan. Do not plan against file contents assumed or remembered from a prior session — sessions end, files change; re-read and re-report every time a plan is about to be produced.

## Output contract

- One scan report per task, produced before the implementation plan
- Every file in the applicable change-type list is either reproduced verbatim under `## FILE: <path>` or explicitly marked `MISSING: <path>`
- No summarization or paraphrase of file contents in the report
- The implementation plan that follows cites the scan report as its basis, not memory or assumption

## Checklist

- [ ] Change type classified (new module / new endpoint / cross-module integration), multiple types merged if applicable
- [ ] Full file list for the classified type(s) enumerated before any file is read
- [ ] Every enumerated file reproduced verbatim under `## FILE: <path>`, or marked `MISSING: <path>`
- [ ] No file summarized or paraphrased in place of its verbatim content
- [ ] Plan production did not begin before the scan report was complete
- [ ] Plan does not rely on remembered or assumed file contents from a prior session
