-- Flyway migration V12
-- Source: database/schema.sql lines 439-451
-- Report module: user-submitted reports on posts, comments, users, stories, messages.

CREATE TABLE reports (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    reporter_id         UUID            NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    report_type         report_type     NOT NULL,
    report_reason       report_reason   NOT NULL,
    entity_id           UUID            NOT NULL,
    description         TEXT,
    status              report_status   NOT NULL DEFAULT 'pending',
    reviewed_by         UUID            REFERENCES users(id) ON DELETE SET NULL,
    reviewed_at         TIMESTAMPTZ,
    resolution_note     TEXT,
    created_at          TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);
