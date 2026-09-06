-- Flyway migration V29
-- Source: database/schema.sql lines 461-473
-- Preserves immutable audit history when the acting administrator account is deleted.

ALTER TABLE admin_actions
    DROP CONSTRAINT admin_actions_admin_id_fkey;

ALTER TABLE admin_actions
    ALTER COLUMN admin_id DROP NOT NULL;

ALTER TABLE admin_actions
    ADD CONSTRAINT admin_actions_admin_id_fkey
    FOREIGN KEY (admin_id) REFERENCES users(id) ON DELETE SET NULL;
