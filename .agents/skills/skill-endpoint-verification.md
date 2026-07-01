---
trigger: model_decision
description: Load any time you need to confirm whether an API endpoint is implemented and functional, not just declared. Defines the mandatory verification sequence and cites the recorded false-positive precedent (ApiConstants.Posts.EXPLORE) so this failure mode is not repeated.
---

# Skill: Endpoint Verification

## When to use

Any time a report, a plan, or an answer to "does endpoint X exist" depends on confirming an endpoint is actually implemented — not merely that a path string for it exists somewhere in the codebase.

## The core rule

A path constant existing in `ApiConstants.java` is **not** evidence that an endpoint is implemented. It is evidence only that a path string exists. `ApiConstants.java` is a constants holder with no relationship to whether any controller ever maps that constant to an HTTP verb. Treating "the constant is defined" as "the endpoint works" is the single most common false-positive in this codebase — see the worked example below.

## Mandatory verification sequence

Run all four steps. Do not stop at step 1 or 2 and report success.

1. Find the constant in `common/ApiConstants.java`. Note its exact value and the nested class it lives under (e.g., `ApiConstants.Posts.EXPLORE`).
2. Find the owning module's interface. If the module uses the OpenAPI Interface Segregation pattern documented in `docs/modules/OPENAPI_GUIDE.md` (an `*Api` interface implemented by the controller), check that interface. Otherwise check the `Controller.java` class directly.
3. Confirm a concrete mapping annotation — `@GetMapping`, `@PostMapping`, `@PutMapping`, `@DeleteMapping`, `@PatchMapping` — references that **exact** constant. A different constant with a similar-looking path string does not count.
4. Confirm the annotated method has a real method body that delegates to a `Service` call. A stub, a `TODO`, an unimplemented-exception throw, or an empty body means the endpoint is declared but not functional — report it as such, not as "implemented."

## Citation requirement

Any "endpoint exists" or "endpoint implemented" claim in a report must cite the exact file and the line or method name where the mapping annotation was found — e.g., `PostController.java:104 — @GetMapping(ApiConstants.Posts.FEED)`. A claim without this citation is invalid. If you cannot produce the citation, the claim has not been verified — re-run the sequence above before including it in any report.

## Worked example — the recorded failure mode in this project

`ApiConstants.Posts` (`common/ApiConstants.java`) declares two adjacent constants:

```java
public static final String FEED = "/feed";
public static final String EXPLORE = "/explore";
```

- `EXPLORE` has no mapping anywhere in `src/main/java/com/app/modules/`. No controller, no `*Api` interface, no handler method references it. Verified by full-codebase search: zero matches outside the constant declaration itself. This is the canonical false positive — the constant exists, so a scan that stops at step 1 would incorrectly report the `/explore` endpoint as present.
- `FEED` sits right next to it in the same nested class and looks identical in kind, which is precisely what makes this a trap: proximity to a working constant does not transfer. `FEED` itself was in the same unmapped state when the `post` controller layer was first scaffolded, and was only wired up later — `PostController.java:104` now has `@GetMapping(ApiConstants.Posts.FEED)` delegating to `postService.getFeed(...)`. That history is the point: a constant's mapping status can change between sessions, so verification must be re-run each time, not carried forward from a prior report.

Do not assume this example is permanently accurate. Re-run the four-step sequence against current code before citing either constant's status — the point of the example is the failure mode (constant existence mistaken for implementation), not a permanently frozen fact about `EXPLORE`.

## Output contract

- Every "endpoint exists" claim cites file + line/method for the mapping annotation
- Every "endpoint exists" claim confirms a real delegating method body, not a stub
- No claim based solely on a constant's presence in `ApiConstants.java`
- Claims re-verified against current code, never carried over from a prior session's report

## Checklist

- [ ] Constant located in `ApiConstants.java` with its exact nested-class path
- [ ] Owning `*Api` interface or `Controller.java` checked for a mapping annotation
- [ ] Mapping annotation confirmed to reference the exact constant (not a lookalike)
- [ ] Method body confirmed to delegate to a `Service` call, not a stub or `TODO`
- [ ] File + line/method citation included in the report
- [ ] Verification re-run against current code, not assumed from memory or a prior report
