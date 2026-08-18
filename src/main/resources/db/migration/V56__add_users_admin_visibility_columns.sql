-- Adds the four columns the administrative user surface reads.
--
-- registration_ip and last_login_ip are INET rather than TEXT so PostgreSQL rejects a malformed
-- address at write time and subnet operators stay available for any future abuse-pattern query.
-- Both are written through IpExtractor, which only honours X-Forwarded-For from a configured
-- trusted proxy, so an untrusted client cannot choose what lands here.
--
-- last_login_at advances only on a real login, never on a token refresh. A value that moved on
-- every refresh would stop being a login signal.
--
-- suspended_until is meaningful only while status = 'suspended'. It is NULL for an indefinite
-- suspension and is cleared whenever the account leaves the suspended state, so a row with
-- status <> 'suspended' and a non-null suspended_until is a defect, not a state to interpret.
--
-- All four are nullable with no default, so this is a catalogue-only change on an existing table.
-- Index creation for these columns is deliberately deferred to V57, which runs outside a
-- transaction so the builds can be CONCURRENTLY.

ALTER TABLE users ADD COLUMN registration_ip INET;
ALTER TABLE users ADD COLUMN last_login_ip   INET;
ALTER TABLE users ADD COLUMN last_login_at   TIMESTAMPTZ;
ALTER TABLE users ADD COLUMN suspended_until TIMESTAMPTZ;
