---
trigger: model_decision
description: Load when creating or modifying request or response DTOs in any module.
---

# Skill: DTO

## When to use
Creating or editing files in `modules/{module}/dto/request/` or `modules/{module}/dto/response/`.

## Input required
- Fields required (from the API contract or DATA_RULES.md)
- Whether it is a request (needs validation) or response (needs Javadoc)

## Steps

### Request DTOs (`dto/request/{Entity}Request.java`)
1. Use a Java `record`:
   ```java
   @Schema(description = "Payload for ...")
   public record {Entity}Request(
       @Schema(description = "...", example = "...", requiredMode = Schema.RequiredMode.REQUIRED)
       @NotBlank
       @Size(min = X, max = Y)
       String fieldName,
       ...
   ) {}
   ```
2. Add `@Schema` at record level (description of the payload) and on each component (description, example, requiredMode).
3. Apply Jakarta validation annotations on every component that needs them: `@NotBlank`, `@NotNull`, `@Email`, `@Size`, `@Pattern`. Optional fields omit `requiredMode`.
4. Do **not** add class-level Javadoc to request records — `@Schema` serves that purpose.
5. Naming: `{Entity}Request` for create/update, `{Entity}LoginRequest` etc. when disambiguation is needed.

### Response DTOs (`dto/response/{Entity}Response.java`)
1. Use a Java `record`:
   ```java
   /**
    * {One sentence describing what this response contains.}
    */
   @Schema(description = "...")
   public record {Entity}Response(
       @Schema(description = "...", example = "...")
       Type fieldName,
       ...
   ) {}
   ```
2. Class-level Javadoc is **required** on response records (per COMMENT_STYLE.md).
3. No validation annotations on response records.
4. No static factory methods unless they encode a domain constant (e.g., `AuthResponse.BEARER`).

### General rules
- Use `record` for both request and response DTOs — do not use `@Data` classes.
- Do not add business logic to DTOs.
- Numeric fields (counts, TTLs): use primitive `long` / `int` unless `null` is a valid value.
- Do not reference entity classes directly from DTOs.

## Output contract
- Request DTOs: `record`, `@Schema` on class and components, Jakarta validation annotations
- Response DTOs: `record`, class-level Javadoc, `@Schema` on class and components
- Both in correct sub-package (`request/` or `response/`)
- No business logic

## Checklist
- [ ] `record` type used (not class)
- [ ] Request: `@Schema` at record level and on each component
- [ ] Request: Jakarta validation annotations on all constrained fields
- [ ] Response: class-level Javadoc present
- [ ] Response: no validation annotations
- [ ] No business logic or entity references
