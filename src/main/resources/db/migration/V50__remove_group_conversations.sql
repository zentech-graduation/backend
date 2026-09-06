-- Flyway migration V50
-- Withdraws group conversations and gives every pair of mutual followers a conversation.
--
-- Group chat is being removed as a product decision, so its rows, columns and constraint all go
-- rather than being left dormant. The deleted rows are archived first: V30 deleted duplicate
-- reports rows with no archive, and this migration deliberately does not repeat that.
--
-- Runs in one transaction. CREATE INDEX CONCURRENTLY is not used here because the column drops
-- below already take an ACCESS EXCLUSIVE lock on conversations, so concurrency buys nothing, and
-- a CONCURRENTLY build that fails leaves an INVALID unique index that silently enforces nothing.

CREATE TABLE archived_group_conversations (
    id                  UUID            PRIMARY KEY,
    group_name          VARCHAR(100),
    group_avatar_url    TEXT,
    created_by          UUID,
    last_message_at     TIMESTAMPTZ,
    created_at          TIMESTAMPTZ,
    archived_at         TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

CREATE TABLE archived_group_participants (
    conversation_id     UUID            NOT NULL,
    user_id             UUID            NOT NULL,
    is_admin            BOOLEAN,
    joined_at           TIMESTAMPTZ,
    left_at             TIMESTAMPTZ,
    last_read_at        TIMESTAMPTZ,
    archived_at         TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    PRIMARY KEY (conversation_id, user_id)
);

CREATE TABLE archived_group_messages (
    id                  UUID            PRIMARY KEY,
    conversation_id     UUID            NOT NULL,
    sender_id           UUID,
    message_type        TEXT,
    content             TEXT,
    media_asset_id      UUID,
    shared_post_id      UUID,
    shared_story_id     UUID,
    reply_to_id         UUID,
    is_deleted          BOOLEAN,
    created_at          TIMESTAMPTZ,
    archived_at         TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- No foreign keys on the archive tables: they must outlive the rows they describe, and a
-- reference back into conversations would be cascaded away by the delete below.

INSERT INTO archived_group_conversations
    (id, group_name, group_avatar_url, created_by, last_message_at, created_at)
SELECT c.id, c.group_name, c.group_avatar_url, c.created_by, c.last_message_at, c.created_at
FROM conversations c
WHERE c.is_group = TRUE;

INSERT INTO archived_group_participants
    (conversation_id, user_id, is_admin, joined_at, left_at, last_read_at)
SELECT p.conversation_id, p.user_id, p.is_admin, p.joined_at, p.left_at, p.last_read_at
FROM conversation_participants p
JOIN conversations c ON c.id = p.conversation_id
WHERE c.is_group = TRUE;

INSERT INTO archived_group_messages
    (id, conversation_id, sender_id, message_type, content, media_asset_id, shared_post_id,
     shared_story_id, reply_to_id, is_deleted, created_at)
SELECT m.id, m.conversation_id, m.sender_id, m.message_type::text, m.content, m.media_asset_id,
       m.shared_post_id, m.shared_story_id, m.reply_to_id, m.is_deleted, m.created_at
FROM messages m
JOIN conversations c ON c.id = m.conversation_id
WHERE c.is_group = TRUE;

-- Now safe to delete. messages and conversation_participants cascade from conversations.
DELETE FROM conversations WHERE is_group = TRUE;

-- Give every existing mutual-follow pair the conversation the application now creates on the
-- follow itself. Without this, relationships formed before this release stay invisible in Messages
-- until one side unfollows and follows again.
WITH mutual AS (
    SELECT DISTINCT
        LEAST(f1.follower_id, f1.following_id)    AS user_a,
        GREATEST(f1.follower_id, f1.following_id) AS user_b
    FROM follows f1
    JOIN follows f2
        ON f2.follower_id = f1.following_id
        AND f2.following_id = f1.follower_id
    WHERE f1.status = 'accepted'
      AND f2.status = 'accepted'
),
missing AS (
    SELECT m.user_a, m.user_b, m.user_a::text || ':' || m.user_b::text AS key
    FROM mutual m
    WHERE NOT EXISTS (
        SELECT 1 FROM conversations c
        WHERE c.direct_pair_key = m.user_a::text || ':' || m.user_b::text
    )
),
created AS (
    INSERT INTO conversations (is_group, direct_pair_key, created_by)
    SELECT FALSE, key, user_a FROM missing
    RETURNING id, direct_pair_key
)
INSERT INTO conversation_participants (conversation_id, user_id)
SELECT c.id, m.user_a FROM created c JOIN missing m ON m.key = c.direct_pair_key
UNION ALL
SELECT c.id, m.user_b FROM created c JOIN missing m ON m.key = c.direct_pair_key;

-- The existing index is partial on is_group, so it must go before the column it depends on.
DROP INDEX IF EXISTS idx_conversations_direct_pair_key;

ALTER TABLE conversations
    DROP COLUMN is_group,
    DROP COLUMN group_name,
    DROP COLUMN group_avatar_url;

ALTER TABLE conversation_participants
    DROP COLUMN is_admin;

-- Recreated without the dropped predicate. Every conversation is 1-1 now, so the pair key alone
-- identifies it. Losing this index would let a pair silently hold two separate threads.
CREATE UNIQUE INDEX idx_conversations_direct_pair_key
    ON conversations (direct_pair_key)
    WHERE direct_pair_key IS NOT NULL;
