-- The two administrative listing indexes that were each found twice and deferred twice.
--
-- Runs outside a transaction, declared in the accompanying V72__*.sql.conf, so both builds can be
-- CONCURRENTLY. users is on the write path of registration and of every login, and hashtags is on
-- the write path of every published post carrying a tag.
--
-- The pair was measured together rather than separately because they were deferred together: each
-- of the two earlier passes found its own gap, saw the identical gap on the other table, and left
-- both alone rather than closing one and leaving the pair inconsistent.
--
-- Measured against 200,000 rows in each table on PostgreSQL 18.6, seeded adversarially: roles in
-- the real distribution so a rare role is the case the index has to win, statuses interleaved by
-- modulus so no index gets physical locality for free, and only 2,000 distinct created_at values so
-- every page boundary lands on a hundred-row tie and the id tiebreaker is load-bearing. Each
-- statement below is the one the corresponding endpoint issues.
--
--   idx_users_role_created        role-filtered account listing
--                                 role = 'user', first page
--                                   without: Incremental Sort over idx_users_created_at
--                                            0.421 ms, 113 buffers
--                                   with:    Index Scan, no sort node
--                                            0.125 ms, 24 buffers
--                                 role = 'moderator', first page
--                                   without: Index Scan on idx_users_created_at discarding 50,600
--                                            rows to return 21. 15.063 ms, 50,754 buffers
--                                   with:    Index Scan. 0.128 ms, 24 buffers
--                                 The rare role is the decisive case and the common one. An
--                                 administrator filters by moderator or admin precisely because
--                                 those are the accounts worth looking at, and that is where the
--                                 without-index plan degrades: the rows it discards scale with the
--                                 whole table, not with the role being listed.
--
--   idx_hashtags_created          administrative hashtag listing with no status filter
--                                 first page
--                                   without: Parallel Seq Scan plus top-N heapsort
--                                            12.938 ms, 1,933 buffers
--                                   with:    Index Scan. 0.192 ms, 24 buffers
--                                 deep page
--                                   without: Parallel Seq Scan plus top-N heapsort
--                                            13.129 ms, 1,903 buffers
--                                   with:    Index Scan. 0.199 ms, 24 buffers
--                                 The existing idx_hashtags_status_created leads with status, which
--                                 an unfiltered listing cannot use at all, so before this index the
--                                 unfiltered case was the only one paying for a full scan.
--
-- Neither index is kept on the strength of an argument. Both change the plan on every statement
-- measured, and the buffer counts stop scaling with the table.

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_users_role_created
    ON users (role, created_at DESC, id DESC);

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_hashtags_created
    ON hashtags (created_at DESC, id DESC);
