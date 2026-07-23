-- Flyway migration V33
-- Adds a deterministic pair key for 1-1 conversations, backed by a partial unique index, as a
-- database-level backstop against duplicate 1-1 conversations for the same pair of users. The
-- primary defense is a Postgres advisory lock taken in ConversationServiceImpl.createDirectConversation;
-- this index catches anything that mechanism does not (e.g. a future write path that bypasses it).

ALTER TABLE conversations ADD COLUMN direct_pair_key TEXT;

-- Backfill: for each existing 1-1 conversation, key = the two participants' user_id values
-- joined in sorted order, matching the LEAST/GREATEST computation the application uses at
-- insert time. If duplicate 1-1 conversations already exist for the same pair, only the oldest
-- receives the key; the rest are left NULL (excluded by the partial index below) rather than
-- failing the migration or merging data.
WITH pair_keys AS (
    SELECT
        cp.conversation_id,
        MIN(cp.user_id::text) || ':' || MAX(cp.user_id::text) AS key
    FROM conversation_participants cp
    GROUP BY cp.conversation_id
    HAVING COUNT(*) = 2
),
ranked AS (
    SELECT
        c.id,
        pk.key,
        ROW_NUMBER() OVER (PARTITION BY pk.key ORDER BY c.created_at, c.id) AS rn
    FROM conversations c
    JOIN pair_keys pk ON pk.conversation_id = c.id
    WHERE c.is_group = FALSE
)
UPDATE conversations c
SET direct_pair_key = ranked.key
FROM ranked
WHERE c.id = ranked.id AND ranked.rn = 1;

CREATE UNIQUE INDEX idx_conversations_direct_pair_key
    ON conversations (direct_pair_key)
    WHERE is_group = FALSE AND direct_pair_key IS NOT NULL;
