-- The two tables behind the warning and strike ladder.
--
-- user_warnings is the record a moderator writes. user_strikes is the consequence three active
-- warnings produce. They are separate tables rather than one table with a kind column because they
-- have different lifecycles: a warning is revoked without touching account status, and a strike is
-- revoked as its own explicit administrator decision.
--
-- admin_action_id is NOT NULL on both. Every row here has a corresponding audit row written in the
-- same transaction, so a discipline record that no audit row explains cannot exist. The FK is what
-- enforces that; the application could not.
--
-- reason_key references report_reason_configs, giving that table its first runtime reader. The
-- reasons a moderator may cite when warning an account are exactly the reasons a user may cite when
-- reporting one, and is_enabled on that row is what takes one out of circulation without a deploy.
--
-- strike_number is CHECK (strike_number >= 1), deliberately not BETWEEN 1 AND 3. An administrator
-- may unban a strike-3 account by hand; a cap would make that account's next strike fail to insert,
-- and rolling the counter back would destroy the history the table exists to keep. Strike 3 and
-- every strike above it carry the same consequence, so the cap would buy nothing.
--
-- revoked_at is the only mutable column on either table. Neither row is ever deleted or rewritten.

CREATE TABLE user_warnings (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    issued_by       UUID        REFERENCES users(id) ON DELETE SET NULL,
    reason_key      VARCHAR(50) NOT NULL REFERENCES report_reason_configs(reason_key),
    note            TEXT        NOT NULL,
    admin_action_id UUID        NOT NULL REFERENCES admin_actions(id),
    revoked_at      TIMESTAMPTZ,
    revoked_by      UUID        REFERENCES users(id) ON DELETE SET NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT user_warnings_note_not_blank CHECK (length(btrim(note)) > 0)
);

CREATE TABLE user_strikes (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    strike_number   SMALLINT    NOT NULL CHECK (strike_number >= 1),
    triggered_by    UUID        REFERENCES users(id) ON DELETE SET NULL,
    admin_action_id UUID        NOT NULL REFERENCES admin_actions(id),
    revoked_at      TIMESTAMPTZ,
    revoked_by      UUID        REFERENCES users(id) ON DELETE SET NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
