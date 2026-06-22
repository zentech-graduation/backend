-- V24: Add expires_at index on refresh_tokens to support efficient purge queries.
-- The RefreshTokenPurgeJob deletes rows where expires_at < (now - grace), which without this
-- index would result in a sequential scan of the growing refresh_tokens table.
-- Note: using plain CREATE INDEX (not CONCURRENTLY) to remain compatible with Flyway's default
-- transactional migration mode. On live databases with existing rows, run CONCURRENTLY manually.
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_expires_at
    ON refresh_tokens (expires_at);
