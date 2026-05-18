---
trigger: model_decision
description: Load when writing or reviewing Java code in App. Defines comment and Javadoc enforcement rules.
---

# Comment Style — App

**Scope**: All source files under `src/main/java/com/app/`
**Language**: Java 21
**Comment language**: English only.

---

## 1. Documentation Comments (Javadoc)

Apply Javadoc only to:

| Target | Required |
|--------|---------|
| All `public` methods in `service/` interfaces | Yes |
| All `public` methods in `controller/` (`@GetMapping`, `@PostMapping`, etc.) | Yes |
| All `public` methods in `mapper/` interfaces with non-obvious mapping logic | Yes |
| All `@Entity` classes (class-level only) | Yes |
| All `@RestController` classes (class-level only) | Yes |
| All `record` types used as API response DTOs | Yes |
| Custom `@Query` methods in `repository/` interfaces | Yes |
| `@Configuration` classes (class-level only) | Yes |

**Not required on**:
- `private` or `package-private` methods
- `@Repository` methods that mirror Spring Data conventions (e.g., `findById`, `findAll`)
- Lombok-generated boilerplate
- Test classes and test methods
- `service/impl/` methods that implement an already-documented interface method without diverging behavior

```java
/**
 * One-sentence summary ending with a period.
 *
 * @param paramName description of what this parameter represents
 * @return description of return value and its semantics
 * @throws ExceptionType when this specific condition occurs
 */
```

- No `@author`, `@version`, or `@since` — Git history is authoritative.
- Document *what* the method does and *why* (business invariants, side effects). Never document *how*.
- Omit `@throws` for unchecked/runtime exceptions unless the caller is expected to handle them.
- Multi-sentence summaries: second sentence starts on a new line after a blank `*` line.

---

## 2. Inline Comments

Use `//` only for non-obvious logic that cannot be made clear by renaming. Place on its own line **above** the code — never at end of line (except for enum constants or ambiguous field names).

```java
// Lua script ensures RPUSH + LTRIM + EXPIRE are atomic across Redis operations
redisTemplate.execute(appendScript, keys, args);

// REQUIRES_NEW isolates the payment reconciliation transaction from the outer batch context
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void reconcilePayment(Long paymentId) { ... }

// Skip bootstrap if index already has documents — partial population is also skipped
if (documentCount > 0) return;
```

Forbidden:
```java
// Call the service        ← restates what the code does
// Get user by id          ← restates the method name
// Loop through the list   ← restates the code
// TODO fix this later     ← no issue reference, FORBIDDEN
// TODO(VR-123): fix this  ← ALLOWED (must have ticket reference in VR-NNN format)
// Created by agent v2     ← no attribution comments
```

---

## 3. Section Dividers — Forbidden

```java
// ===================== DO NOT USE =====================
// *** Forbidden ***
/* ---- Also forbidden ---- */
//===================================================
```

`hooks/pre-commit-lint.sh` blocks commits containing these patterns.

---

## 4. Comment Placement

- Inline comments sit on their own line **above** the code they describe with no blank line between them.
- Trailing same-line comments allowed **only** for enum constants or fields where the name is ambiguous:

```java
public enum JobStatus {
    DRAFT,
    PUBLISHED, // Visible to candidates, consumes quota
    CLOSED     // No new applications accepted
}
```

---

## 5. Removed / Commented-Out Code

Dead code must be deleted, not commented out. `hooks/pre-commit-lint.sh` flags 3+ consecutive `//` comment lines as a violation. If reactivation is uncertain, create a Git branch or a tracked issue (`VR-NNN`).

---

## 6. Layer-Specific Rules

| Layer | Rule |
|-------|------|
| `controller/` | Javadoc on every handler method: one sentence describing the HTTP action, what it accepts, what it returns, and auth requirements. |
| `service/` interface | Javadoc on every method — these are the domain API contracts. Document business invariants and side effects. |
| `service/impl/` | Javadoc only if the implementation diverges meaningfully from the interface contract. Use inline `//` for non-obvious transaction boundaries or external call patterns. |
| `repository/` | Javadoc on custom `@Query` methods only. Standard Spring Data method names need no comment. |
| `entity/` | Class-level Javadoc required. Field-level Javadoc only for fields with business constraints (nullable semantics, computed fields, state machine fields). |
| `mapper/` | No comments unless a mapping involves non-obvious transformation logic. |
| `dto/` | No comments unless a field name is a domain abbreviation opaque to a new engineer. |
| `config/` | Class-level Javadoc required. Inline comments for non-default configuration values where the reason is non-obvious. |
| `common/security/` | Document security contracts and token lifecycle invariants on class-level Javadoc. |

### AI Module Specifics

```java
// Combines job description and candidate profile to generate a relevance score (0–100)
String prompt = promptBuilder.build(job, candidate);

// Key format: ai:emb:{sha256(text)} — TTL 24hr, avoids redundant OpenAI embedding calls
String cacheKey = "ai:emb:" + DigestUtils.sha256Hex(text);

// Key format: ai:mem:{userId}:{sessionId} — TTL 1hr, capped at 10 messages via Lua script
```

### Transaction Boundary Annotations

Always document `REQUIRES_NEW` propagation with an inline comment explaining the isolation reason:

```java
// Ensures payment activation is committed independently — outer batch failure must not roll it back
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void activateSubscription(Long paymentId) { ... }
```

---

## 7. Framework Annotations — No Comment Required

```java
@RestController
@RequestMapping("/app/...")
@Service
@Repository
@Component
@Configuration
@Bean
@Autowired
@Value("...")
@Cacheable
@Transactional          // No comment needed UNLESS propagation is non-default
@PreAuthorize("...")
@Valid
@NotNull, @NotBlank, @Size, etc.
@Getter, @Setter, @Builder, @Data, @AllArgsConstructor, @NoArgsConstructor
@Entity, @Table, @Column
@Id, @GeneratedValue
```

Exception: add an inline comment if a configuration value is non-default and the reason is non-obvious (e.g., `@Transactional(propagation = REQUIRES_NEW)` must always have a comment).

---

## 8. Enforcement Checklist

Before committing any source file, verify:

- [ ] No commented-out code exists.
- [ ] No decorative dividers (`====`, `----`, `***`, `███`, etc.).
- [ ] No `TODO` / `FIXME` / `HACK` without a tracked issue reference in `VR-NNN` format.
- [ ] No attribution comments (`// added by`, `// agent`, `// created`, `// author`).
- [ ] Javadoc exists on all `public` service interface method declarations.
- [ ] Javadoc exists on all `@RestController` handler methods.
- [ ] Javadoc exists on all `@Entity` and `@Configuration` class declarations.
- [ ] Inline comments explain *why*, never *what*.
- [ ] Comment language is English throughout.
- [ ] No `System.out.println` or `e.printStackTrace()` anywhere in production code.
- [ ] All `@Transactional(propagation = REQUIRES_NEW)` usages have an inline comment explaining the isolation rationale.
