-- The two indexes the hashtag lifecycle needs.
--
-- Runs outside a transaction, declared in the accompanying V68__*.sql.conf, so both builds can be
-- CONCURRENTLY. hashtags is on the write path of every published post that carries a tag.
--
-- Measured against 200,000 hashtag rows on PostgreSQL 18.6, seeded adversarially: every name shares
-- the same trigram substring so a trigram match cannot be selective, statuses interleaved by
-- modulus at 97 per cent active / 2 per cent banned / 1 per cent deleted so no index gets physical
-- locality for free, only 2,000 distinct created_at values so every page boundary lands on a
-- hundred-row tie and the id tiebreaker is load-bearing, and a heavy-tailed post_count. Each
-- statement below is the one the corresponding code path issues, run at a deep cursor position.
--
--   idx_hashtags_status_created   administrative listing narrowed by status, deep page
--                                 without: Parallel Seq Scan, 99,000 rows discarded per worker,
--                                          then a top-N heapsort. 9.69 ms, 1,903 buffers
--                                 with:    Index Scan, both the status equality and the row-value
--                                          keyset predicate resolved as one index condition, no
--                                          sort at all. 0.22 ms, 24 buffers
--                                 The discarded row count scales with the whole table rather than
--                                 with the status being listed, so the without-index plan degrades
--                                 on every call as the registry grows.
--
--   idx_hashtags_active_post_count  public hashtag search, the pg_trgm fallback path
--                                 without: Index Scan on idx_hashtags_post_count feeding an
--                                          Incremental Sort, because that index carries post_count
--                                          but not the name tiebreaker and nothing about status.
--                                          7.22 ms
--                                 with:    Index Scan on this index alone. The partial predicate
--                                          absorbs the status filter and the index order supplies
--                                          both sort keys, so the Incremental Sort node disappears
--                                          from the plan entirely. 1.23 ms
--                                 6,112 kB, against 11 MB for the existing name unique index.
--
-- Two things this migration deliberately does not do.
--
-- It adds no index for findBannedNames. That query is
--   SELECT name FROM hashtags WHERE status = 'banned' AND name IN (...)
-- and the existing unique index on name already serves it as an Index Scan issuing one search per
-- probed name, filtering the handful of matched rows on status. Measured identical with and
-- without both indexes above. A status-leading index would have to scan every banned row instead,
-- which is strictly worse: the caption supplies at most a few dozen names while the banned set
-- grows without bound.
--
-- It adds no index for the administrative listing when no status filter is supplied. That query
-- orders by created_at alone, which an index leading with status cannot serve, and it measured
-- identical with and without: Parallel Seq Scan plus top-N heapsort, 14.1 ms. The same gap exists
-- on users, whose V57 added idx_users_status_created and no unfiltered equivalent, so closing it
-- here alone would be inconsistent. Recorded rather than fixed.

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_hashtags_active_post_count
    ON hashtags (post_count DESC, name ASC) WHERE status = 'active';

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_hashtags_status_created
    ON hashtags (status, created_at DESC, id DESC);
