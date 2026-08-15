#!/usr/bin/env bash
#
# Seeds a local development database with a small, fixed set of accounts and content.
#
# WHAT IT CREATES
#
#   Five accounts, all with the password SeedPass123! and all pre-verified:
#
#     seed_alice  alice@seed.local  role user       public account, two posts
#     seed_bob    bob@seed.local    role user       private account
#     seed_carol  carol@seed.local  role user       public account, one post
#     seed_mod    mod@seed.local    role moderator  moderation surfaces
#     seed_admin  admin@seed.local  role admin      admin surfaces
#
#   A follow graph: carol and alice follow each other (accepted), and alice has a
#   pending follow request against bob, who is private.
#
#   Three published text posts.
#
#   Denormalised counters (follower_count, following_count, post_count) are left to the
#   database triggers, per the project rule that application code never writes them.
#
# WHAT IT ASSUMES
#
#   - Run from anywhere; the script resolves the repository root from its own location.
#   - A .env at the repository root supplying POSTGRES_URL, POSTGRES_USER and POSTGRES_DB.
#   - The compose stack is up and Flyway has migrated the schema, which happens when the
#     application starts. The script refuses to run against an unmigrated database.
#   - The docker CLI is on PATH. No psql binary and no Python are needed on the host.
#
# WHY DIRECT SQL RATHER THAN THE HTTP API
#
#   The API route does work end to end: registration delivers a verification link to the local
#   Mailpit sink, and following it verifies the account. It is rejected here for two reasons
#   that have nothing to do with whether it works.
#
#   It needs the application, RabbitMQ and Mailpit all up and healthy before a single account
#   exists, which makes seeding fail for reasons unrelated to seeding. This script needs only
#   the compose PostgreSQL service, which has to be up regardless.
#
#   It also cannot be made idempotent. Re-registering an address is a conflict, not a no-op,
#   so a second run would either error or need to interpret the conflict as success, and the
#   second of those hides a genuine failure.
#
#   pgcrypto is enabled by migration V01, so crypt(..., gen_salt('bf', 12)) produces a BCrypt
#   hash that the application's BCryptPasswordEncoder(12) verifies natively.
#
# IDEMPOTENCY
#
#   Safe to run repeatedly. Every write is guarded by a natural key, so a second run inserts
#   nothing and reports zero rows affected. It never updates or deletes existing rows.
#
# SAFETY
#
#   Refuses to run unless POSTGRES_URL points at localhost and the active Docker context is a
#   local socket. It cannot be pointed at a remote or production database.

set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

SEED_PASSWORD='SeedPass123!'

fail() {
    echo "seed-dev-data: $*" >&2
    exit 1
}

read_env() {
    local key="$1" value
    value="$(sed -nE "s/^[[:space:]]*${key}[[:space:]]*=[[:space:]]*(.*)[[:space:]]*$/\1/p" .env | tail -n 1)"
    value="${value%\"}"
    value="${value#\"}"
    value="${value%\'}"
    value="${value#\'}"
    printf '%s' "$value"
}

[[ -f .env ]] || fail "no .env at $REPO_ROOT. Copy .env.example to .env and fill it in."

POSTGRES_URL="$(read_env POSTGRES_URL)"
POSTGRES_USER="$(read_env POSTGRES_USER)"
POSTGRES_DB="$(read_env POSTGRES_DB)"

[[ -n "$POSTGRES_URL"  ]] || fail "POSTGRES_URL is not set in .env."
[[ -n "$POSTGRES_USER" ]] || fail "POSTGRES_USER is not set in .env."
[[ -n "$POSTGRES_DB"   ]] || fail "POSTGRES_DB is not set in .env."

# First guard. The seed writes unverified accounts with a shared, published password, so it must
# never reach a database that is not disposable.
case "$POSTGRES_URL" in
    *//localhost:*|*//127.0.0.1:*|*//[::1]:*) ;;
    *) fail "refusing to run. POSTGRES_URL is '$POSTGRES_URL', which is not a local database." ;;
esac

# Second guard. A local POSTGRES_URL is not sufficient on its own: a remote Docker context or a
# tcp/ssh DOCKER_HOST would send `docker compose exec` to another machine's compose stack.
docker_endpoint="${DOCKER_HOST:-$(docker context inspect --format '{{.Endpoints.docker.Host}}' 2>/dev/null || true)}"
case "$docker_endpoint" in
    unix://*|npipe://*|"") ;;
    *) fail "refusing to run. The Docker endpoint is '$docker_endpoint', which is not local." ;;
esac

psql_exec() {
    docker compose exec -T postgres psql -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB" "$@"
}

docker compose ps --status running --services 2>/dev/null | grep -qx postgres \
    || fail "the compose postgres service is not running. Start it with: docker compose up -d"

psql_exec -tAc "SELECT to_regclass('public.users');" | grep -qx users \
    || fail "the schema is not migrated. Start the application once so Flyway runs, then retry."

echo "seed-dev-data: seeding $POSTGRES_DB via the compose postgres service"

psql_exec <<SQL
BEGIN;

INSERT INTO users (username, email, display_name, role, status, is_private)
VALUES
    ('seed_alice', 'alice@seed.local', 'Seed Alice', 'user',      'active', FALSE),
    ('seed_bob',   'bob@seed.local',   'Seed Bob',   'user',      'active', TRUE),
    ('seed_carol', 'carol@seed.local', 'Seed Carol', 'user',      'active', FALSE),
    ('seed_mod',   'mod@seed.local',   'Seed Mod',   'moderator', 'active', FALSE),
    ('seed_admin', 'admin@seed.local', 'Seed Admin', 'admin',     'active', FALSE)
ON CONFLICT DO NOTHING;

-- Pre-verified on purpose. Login is blocked until the address is verified, and a seeded account
-- that arrived through SQL has no outbound verification mail to follow.
INSERT INTO user_credentials (user_id, password_hash, email_verified, email_verified_at)
SELECT u.id, crypt('${SEED_PASSWORD}', gen_salt('bf', 12)), TRUE, NOW()
FROM users u
WHERE u.email LIKE '%@seed.local'
ON CONFLICT (user_id) DO NOTHING;

-- Status is set explicitly rather than left to a default: no trigger derives 'pending' from the
-- target's is_private flag, the application does, and this script does not go through it.
INSERT INTO follows (follower_id, following_id, status)
SELECT f.id, t.id, e.status::follow_status
FROM (VALUES
    ('seed_carol', 'seed_alice', 'accepted'),
    ('seed_alice', 'seed_carol', 'accepted'),
    ('seed_alice', 'seed_bob',   'pending')
) AS e(follower, following, status)
JOIN users f ON f.username = e.follower
JOIN users t ON t.username = e.following
ON CONFLICT DO NOTHING;

-- posts has no natural unique key, so the caption carries the idempotency guard.
INSERT INTO posts (user_id, caption, post_type, status)
SELECT u.id, e.caption, 'text'::post_type, 'published'::post_status
FROM (VALUES
    ('seed_alice', '[seed] alice public post one'),
    ('seed_alice', '[seed] alice public post two'),
    ('seed_carol', '[seed] carol public post one')
) AS e(username, caption)
JOIN users u ON u.username = e.username
WHERE NOT EXISTS (
    SELECT 1 FROM posts p WHERE p.user_id = u.id AND p.caption = e.caption
);

COMMIT;
SQL

echo
echo "seed-dev-data: accounts now present"
psql_exec -c "
SELECT u.username,
       u.email,
       u.role,
       u.is_private,
       c.email_verified,
       u.follower_count,
       u.following_count,
       u.post_count
FROM users u
JOIN user_credentials c ON c.user_id = u.id
WHERE u.email LIKE '%@seed.local'
ORDER BY u.username;
"

echo "seed-dev-data: done. Every seeded account uses the password ${SEED_PASSWORD}"
