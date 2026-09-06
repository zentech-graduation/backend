---
trigger: model_decision
description: Load when creating a new Flyway SQL migration file.
---

# Skill: Flyway Migration

## When to use
Adding any new `src/main/resources/db/migration/V{NN}__*.sql` file.

## Input required
- Next available version number (inspect existing migrations to find max V-number)
- What schema change is needed (table, index, trigger, enum value, etc.)
- Reference from `database/schema.sql` if adding a table that is already defined there

## Steps

1. Determine the next version: `ls src/main/resources/db/migration/ | sort` → use V{max+1}.
2. Name the file: `V{NN}__{snake_case_description}.sql` — double underscore between version and description.
3. Write the header comment block:
   ```sql
   -- Flyway migration V{NN}
   -- Source: database/schema.sql lines {X}-{Y}   (if applicable)
   -- Brief description of what this migration creates or alters.
   ```
4. SQL style rules:
   - All SQL keywords UPPERCASE (`CREATE TABLE`, `NOT NULL`, `DEFAULT`, `REFERENCES`, `ON DELETE CASCADE`, etc.)
   - Table and column names in lowercase `snake_case`
   - One column per line in `CREATE TABLE`, aligned by logical group
5. Creating PostgreSQL enum types:
   ```sql
   CREATE TYPE type_name AS ENUM ('value1', 'value2', ...);
   ```
   Enum types are defined in V01. To add a new value, use `ALTER TYPE type_name ADD VALUE 'new_value'` in a new migration.
6. Creating tables:
   - Primary key: `UUID PRIMARY KEY DEFAULT gen_random_uuid()` for standalone tables; `UUID PRIMARY KEY REFERENCES parent(id) ON DELETE CASCADE` for FK-as-PK tables.
   - All timestamps: `TIMESTAMPTZ NOT NULL DEFAULT NOW()` for `created_at`; `TIMESTAMPTZ NOT NULL DEFAULT NOW()` for `updated_at` (trigger maintains the live value).
   - Denormalized counter columns: include `CHECK (counter_col >= 0)`.
   - Soft-delete column: `deleted_at TIMESTAMPTZ` — no default, nullable.
7. Creating indexes (V15 pattern): in a dedicated migration or as part of the table migration when closely related.
   ```sql
   CREATE INDEX idx_{table}_{col} ON {table}({col});
   CREATE INDEX idx_{table}_deleted_at ON {table}(deleted_at) WHERE deleted_at IS NULL;
   ```
8. Creating triggers and functions (V16 pattern):
   ```sql
   CREATE OR REPLACE FUNCTION fn_name()
   RETURNS TRIGGER AS $$
   BEGIN
       ...
       RETURN NEW;  -- or OLD for DELETE
   END;
   $$ LANGUAGE plpgsql;

   CREATE TRIGGER trg_name
       BEFORE UPDATE ON table_name
       FOR EACH ROW EXECUTE FUNCTION fn_name();
   ```
9. `database/schema.sql` is a reference document — **do not** apply it via Flyway and do not modify it as part of adding a migration. It describes the final intended schema state.
10. Flyway is configured with `out-of-order: true`; still use sequential version numbers to avoid gaps.

## Output contract
- File at `src/main/resources/db/migration/V{NN}__{description}.sql`
- Header comment present
- SQL keywords uppercase
- Double underscore in filename
- No modification to `database/schema.sql`

## Checklist
- [ ] Version number is max + 1
- [ ] Double underscore in filename
- [ ] Header comment written
- [ ] SQL keywords uppercase
- [ ] UUIDs use `gen_random_uuid()`
- [ ] Timestamps use `TIMESTAMPTZ`
- [ ] Counter columns have `CHECK (col >= 0)`
- [ ] `database/schema.sql` not modified
- [ ] `./mvnw spring-boot:run` (or test) validates migration applies cleanly
