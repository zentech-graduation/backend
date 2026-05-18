---
trigger: model_decision
description: Load when creating or modifying a JPA @Entity class in any module.
---

# Skill: JPA Entity

## When to use
Creating or editing any class in `modules/{module}/entity/`.

## Input required
- Module name and corresponding table name (from `database/schema.sql` or Flyway migration)
- List of columns to map (check the migration for which are denormalized counters — exclude them)

## Steps

1. Read `docs/modules/{module}/DATA_RULES.md` and `docs/modules/GLOBAL_RULES.md` before writing any field.
2. Add class-level Javadoc: one sentence naming the table, one sentence noting any excluded columns (counters, trigger-maintained fields) and why.
3. Annotate the class:
   ```java
   @Entity
   @Table(name = "table_name")
   @Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
   ```
4. Declare the `@Id` field:
   - If the DB generates it (most tables): `@Id @GeneratedValue(strategy = GenerationType.AUTO) @Column(name = "id", nullable = false, updatable = false) private UUID id;`
   - If it is a FK-as-PK (e.g., `user_credentials`, `user_settings`): `@Id @Column(name = "user_id", nullable = false, updatable = false) private UUID userId;` — no `@GeneratedValue`.
5. Map PostgreSQL enum columns with `@Convert`:
   ```java
   @Convert(converter = XxxConverter.class)
   @Column(name = "col", nullable = false, columnDefinition = "pg_enum_type_name")
   private XxxEnum field;
   ```
   Do NOT use `@Enumerated(EnumType.STRING)`.
6. Map audit timestamps:
   - `@CreationTimestamp @Column(name = "created_at", nullable = false, updatable = false) private OffsetDateTime createdAt;`
   - `@UpdateTimestamp @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt;`
   - Both from `org.hibernate.annotations`.
7. Map `deleted_at` as a plain nullable field: `@Column(name = "deleted_at") private OffsetDateTime deletedAt;` — no annotation.
8. Use `columnDefinition = "TEXT"` for unbounded text columns; `columnDefinition = "inet"` for IP addresses.
9. **Do not map** denormalized counter columns (`follower_count`, `like_count`, etc.) — they are trigger-maintained and must never be written from application code.
10. **Do not map** JPA relationships (`@OneToMany`, `@ManyToOne`, `@JoinColumn`) — use plain `UUID` FK fields only.
11. **Do not add** business logic or `@Transactional` to entities.
12. If a unique constraint spans multiple columns, use `@Table(... uniqueConstraints = @UniqueConstraint(...))`.

## Output contract
- File in `modules/{module}/entity/{EntityName}.java`
- Class-level Javadoc present
- All Lombok annotations: `@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder`
- No JPA relationship annotations
- No denormalized counter fields
- No business methods
- Enum fields use `@Convert`, not `@Enumerated`
- Timestamps use `OffsetDateTime`

## Checklist
- [ ] Class-level Javadoc written
- [ ] `@Entity @Table` present with correct table name
- [ ] All five Lombok annotations present
- [ ] ID strategy matches table design (generated vs FK-as-PK)
- [ ] PostgreSQL enums mapped with `@Convert + columnDefinition`
- [ ] Audit fields use `@CreationTimestamp` / `@UpdateTimestamp`
- [ ] No counter columns mapped
- [ ] No `@OneToMany` / `@ManyToOne` / `@JoinColumn`
