-- Flyway migration V02
-- Source: database/schema.sql lines 61-139
-- Auth module: users, user_credentials, oauth_accounts, refresh_tokens.
-- Email-verification and password-reset tokens are stored in Redis (see TokenServiceImpl).

CREATE TABLE users (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    username            VARCHAR(30)     UNIQUE NOT NULL,
    email               VARCHAR(255)    UNIQUE NOT NULL,
    display_name        VARCHAR(100),
    bio                 TEXT,
    avatar_url          TEXT,
    website_url         TEXT,
    role                user_role       NOT NULL DEFAULT 'user',
    status              user_status     NOT NULL DEFAULT 'active',
    is_private          BOOLEAN         NOT NULL DEFAULT FALSE,
    is_verified         BOOLEAN         NOT NULL DEFAULT FALSE,
    follower_count      INT             NOT NULL DEFAULT 0 CHECK (follower_count >= 0),
    following_count     INT             NOT NULL DEFAULT 0 CHECK (following_count >= 0),
    post_count          INT             NOT NULL DEFAULT 0 CHECK (post_count >= 0),
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMPTZ
);

CREATE TABLE user_credentials (
    user_id             UUID            PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    password_hash       TEXT,
    email_verified      BOOLEAN         NOT NULL DEFAULT FALSE,
    email_verified_at   TIMESTAMPTZ,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE TABLE oauth_accounts (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID            NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider            oauth_provider  NOT NULL,
    provider_id         VARCHAR(255)    NOT NULL,
    provider_email      VARCHAR(255),
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    UNIQUE (provider, provider_id)
);

CREATE TABLE refresh_tokens (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID            NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash          TEXT            NOT NULL UNIQUE,
    device_id           VARCHAR(255),
    user_agent          TEXT,
    ip_address          INET,
    expires_at          TIMESTAMPTZ     NOT NULL,
    revoked_at          TIMESTAMPTZ,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

