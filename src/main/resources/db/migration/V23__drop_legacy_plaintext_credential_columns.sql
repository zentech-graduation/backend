-- V23: Drop legacy plaintext credential columns if they exist.
-- These columns were removed from oauth_accounts during an in-place migration rewrite.
-- Databases that executed the original migration still carry these columns.
-- This migration removes them safely on any database regardless of which
-- variant of the original migration was applied.
ALTER TABLE oauth_accounts DROP COLUMN IF EXISTS access_token;
ALTER TABLE oauth_accounts DROP COLUMN IF EXISTS refresh_token;
ALTER TABLE oauth_accounts DROP COLUMN IF EXISTS token_expires_at;
