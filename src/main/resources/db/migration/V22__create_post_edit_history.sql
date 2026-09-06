-- Flyway migration V22
-- Post module: post_edit_history (append-only caption edit audit).
-- Rows are never updated or soft-deleted; deletion happens only via post/user hard-delete cascade.

CREATE TABLE post_edit_history (
    id                  UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id             UUID            NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    editor_id           UUID            NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    previous_caption    TEXT,
    edited_at           TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_post_edit_history_post_edited
    ON post_edit_history (post_id, edited_at DESC);
