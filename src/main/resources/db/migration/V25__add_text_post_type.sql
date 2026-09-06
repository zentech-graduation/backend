-- ALTER TYPE ... ADD VALUE does not require a manual BEGIN/COMMIT block.
-- Flyway wraps the migration in a transaction automatically; PostgreSQL 12+
-- allows ALTER TYPE ADD VALUE inside a transaction.
ALTER TYPE post_type ADD VALUE 'text';
