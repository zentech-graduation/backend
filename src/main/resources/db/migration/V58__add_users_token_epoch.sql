-- Adds the per-user token epoch that lets an administrator invalidate access tokens immediately.
--
-- Before this column, force logout and role change revoked refresh tokens but left every access
-- token already in the target's hands valid for the remainder of ACCESS_TOKEN_TTL, because the
-- blacklist is keyed on the token's own jti and no administrator holds it. Measured at 900 seconds.
--
-- Every access token now carries the issuing account's token_epoch as a claim, and the principal
-- resolver rejects a token whose claim does not equal this column. Incrementing the column
-- therefore invalidates every token minted before the increment, in one write, on both the REST
-- path and the WebSocket sweep, because both go through the same resolver.
--
-- DEFAULT 0 matches the value the resolver reads for a token that carries no epoch claim at all,
-- so tokens minted before this migration stay valid rather than logging out every user at once
-- the moment it is applied.
--
-- The column is added with a default and NOT NULL, which PostgreSQL 11 and later record in the
-- catalogue without rewriting the table.

ALTER TABLE users ADD COLUMN token_epoch INTEGER NOT NULL DEFAULT 0;
