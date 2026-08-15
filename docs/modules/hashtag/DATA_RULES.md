# Hashtag Module — Data Rules

**Implementation status**: Fully implemented.
Caption hashtag extraction and normalization at publish time, upsert-on-first-use, removal on unpublish or soft delete, trending snapshot generation, and Elasticsearch-backed search with a PostgreSQL trigram fallback are all in place.

---

## Section 1: Canonical Data

| Table | Key Columns | Notes |
|-------|-------------|-------|
| `hashtags` | `id`, `name`, `post_count`, `created_at` | Canonical hashtag registry. `name` is stored without the `#` prefix. Created on first use. |
| `post_hashtags` | `post_id`, `hashtag_id`, `created_at` | Junction table associating posts to hashtags. Canonical many-to-many relationship. |
| `hashtag_trending` | `hashtag_id`, `period_start`, `period_end`, `post_count`, `rank` | Periodic trending snapshots. Populated by a background scheduler. Not real-time. |

These tables cannot be rebuilt from any other source if lost.

---

## Section 2: Derived Data / Cache / Projection

| Data | Location | Rebuilt From | Rebuild Trigger |
|------|----------|--------------|-----------------|
| `hashtags.post_count` | `hashtags` table | `COUNT(*)` from `post_hashtags` where `hashtag_id = hashtag.id` | Trigger `trg_hashtag_post_count` (V16) |
| `hashtag_trending` rows | `hashtag_trending` table | Aggregated from `post_hashtags` over a time window by background job | Scheduled background job |
| Hashtag search index | GIN index `idx_hashtags_name_trgm` | Rebuild by reindexing `hashtags.name` | Automatic on write |

---

## Section 3: Business Rules

### A. Rules Enforced by the Database

| Rule | Enforced By |
|------|-------------|
| `hashtags.name` must be unique (case-sensitive as stored) | `UNIQUE NOT NULL` on `hashtags.name` |
| `hashtags.name` max 100 characters | `VARCHAR(100)` |
| `hashtags.post_count` is non-negative | `CHECK (post_count >= 0)` |
| `post_hashtags` allows at most one association per (post, hashtag) pair | Compound `PRIMARY KEY (post_id, hashtag_id)` |
| Deleting a post cascades to its `post_hashtags` rows (and decrements `hashtags.post_count` via trigger) | `ON DELETE CASCADE` on `post_hashtags.post_id` |
| Deleting a hashtag cascades to its `post_hashtags` rows and `hashtag_trending` rows | `ON DELETE CASCADE` on FK references |
| `hashtag_trending` PK is `(hashtag_id, period_start)` — one trending record per hashtag per period | Compound `PRIMARY KEY` |

### B. Rules Enforced by Application Code

| Rule | Service / Component |
|------|---------------------|
| Hashtags are extracted from post `caption` at publish time using `#word` parsing | `PostServiceImpl.extractHashtags` |
| Hashtag names must be normalized (lowercase, trimmed) before lookup or insert | `HashtagServiceImpl.normalize` |
| A hashtag row is created if it does not exist (`INSERT ... ON CONFLICT DO NOTHING` or upsert) when a post uses it | `HashtagRepository.upsertByName` |
| When a post is unpublished or soft-deleted, its `post_hashtags` rows must be deleted (triggering `post_count` decrement) | `HashtagServiceImpl.removeHashtagsForPost`, called from `PostServiceImpl` on archive and on soft delete |
| Hashtag search uses the `idx_hashtags_name_trgm` GIN index for fuzzy matching | `HashtagRepository.searchByNameTrgm`, the PostgreSQL fallback `HashtagSearchServiceImpl` degrades to when Elasticsearch is unavailable; Elasticsearch is the primary path |
| The trending background job writes to `hashtag_trending` with a `(period_start, period_end)` window and a computed `rank` | `HashtagTrendingServiceImpl.runTrendingJob` / `snapshotTrending` |

**Normalization before insert**:
- `hashtags.name` is stored lowercase. The DB UNIQUE constraint is case-sensitive.
- The Service layer MUST normalize hashtag names to lowercase before any insert or lookup.
- Names longer than 100 characters (the `hashtags.name` column bound) are dropped during normalization, not truncated, so a truncated prefix never aliases a legitimately distinct shorter tag.
- Failure to normalize before insert will result in duplicate hashtags differing only by case, bypassing the uniqueness guarantee.
- Rule owner: `HashtagService` — normalize to lowercase before `findByName` or `save`.

### C. Scope Simplifications

- `hashtag_trending` is populated by a periodic batch job; trending data may be minutes or hours stale.
- No real-time trending calculation.
- No hashtag following (users cannot subscribe to a hashtag).

---

## Section 4: Inter-Module Dependencies

| Dependency | Direction | Nature |
|------------|-----------|--------|
| `post` | inbound | `post_hashtags` links posts to hashtags; trigger fires on `post_hashtags` insert/delete |
| `recommendation` | inbound | `user_events` records `hashtag_click` events referencing `entity_type = 'hashtag'` |
