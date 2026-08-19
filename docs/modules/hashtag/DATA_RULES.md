# Hashtag Module — Data Rules

**Implementation status**: Fully implemented.
Caption hashtag extraction and normalization at publish time, upsert-on-first-use, removal on unpublish or soft delete, trending snapshot generation, Elasticsearch-backed search with a PostgreSQL trigram fallback, and the administrative lifecycle that lets a term be taken out of circulation are all in place.

---

## Section 1: Canonical Data

| Table | Key Columns | Notes |
|-------|-------------|-------|
| `hashtags` | `id`, `name`, `post_count`, `status`, `status_note`, `status_at`, `status_by`, `created_at` | Canonical hashtag registry. `name` is stored without the `#` prefix. Created on first use, always as `active`. The four status columns record an administrator's lifecycle decision and who took it. |
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
| `hashtags.status` must be one of `'active'`, `'banned'`, `'deleted'`; defaults to `'active'` | `hashtag_status` enum, `DEFAULT 'active'` (V67) |
| `status_by` becomes NULL if the deciding administrator's account is deleted | `ON DELETE SET NULL` on `status_by` FK |
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
| Hashtag search uses the `idx_hashtags_name_trgm` GIN index for fuzzy matching, narrowed to `status = 'active'` | `HashtagRepository.searchByNameTrgm`, the PostgreSQL fallback `HashtagSearchServiceImpl` degrades to when Elasticsearch is unavailable; Elasticsearch is the primary path. There is no separate autocomplete endpoint, so `GET /hashtags/search` is the whole of that surface |
| The trending background job writes to `hashtag_trending` with a `(period_start, period_end)` window and a computed `rank`, counting only hashtags whose `status` is `active` | `HashtagTrendingServiceImpl.runTrendingJob` / `snapshotTrending` |
| Taking a hashtag out of circulation deletes its `hashtag_trending` rows in the same transaction as the status change | `HashtagLifecycleServiceImpl.changeStatus` - a hashtag is usually banned in reaction to something happening right now, which is exactly when it is at the top of the trending list, so waiting for the next job cycle leaves it there for the worst possible hour. The job filter above is the second half of the same rule: it stops the next snapshot putting the tag straight back |
| A status change enqueues the ordinary index-sync event, and the consumer resolves it against current state | `HashtagLifecycleServiceImpl.changeStatus`, `HashtagIndexSyncConsumer` - the envelope carries the hashtag id only and the consumer reads `status` and `post_count` from the database, so an upsert event becomes an index delete the moment the row says the tag has left circulation. No event type was added for this |
| `upsertByName` must never resurrect a hashtag an administrator has taken out of circulation | `HashtagRepository.upsertByName` - `ON CONFLICT (name) DO NOTHING` leaves the existing row untouched, so first-use traffic cannot write over the decision. Pinned by `HashtagRepositoryIT`, because the danger is a later change to `DO UPDATE` |
| A post write path may not associate a banned hashtag | `HashtagService.findBannedNames` answers a whole caption in one statement; `upsertHashtagsForPost` refuses outright, and `upsertHashtagsForPostSkippingBanned` is the single entry point that associates the rest and reports what it skipped. See `post/DATA_RULES.md` for where each is called |
| The administrative registry reads span every status | `HashtagRepositoryCustom` - the inversion of the `status = 'active'` predicate every public read carries is contained to that one interface, so no public read path can pick up an unfiltered query by accident |

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
- There is no hashtag detail endpoint and no posts-by-hashtag endpoint. `GET /hashtags/search` and `GET /hashtags/trending` are the whole public surface, so the status filter has two public readers rather than four.
- The administrative listing with no status filter has no index. It orders by `created_at` alone, which `idx_hashtags_status_created` cannot serve because that index leads with `status`. Measured at 14 ms against 200,000 rows: a parallel sequential scan plus a top-N heapsort. `users` has the same gap for the same reason, so closing it here alone would be inconsistent.

---

### D. The Lifecycle, and the Line It Does Not Cross

`hashtag_status` has three values.
No transition between two different values is forbidden; a move to the state already held is refused with `ADMIN_INVALID_TRANSITION`, because there is nothing to record.

| State | Reached by | Effect |
|-------|-----------|--------|
| `active` | first use, or an administrator returning a tag to circulation | discoverable everywhere, accepted on every write |
| `banned` | an administrator | absent from hashtag search, from trending and from the search index; refused on every post write path with `422 POST_BANNED_HASHTAG` |
| `deleted` | an administrator | as `banned`, and additionally left out of the hashtag list on a post response |

**Deleting is never a row delete.**
`DELETE /api/v1/admin/hashtags/{hashtagId}` sets the status.
Removing the row would cascade to `post_hashtags` and drive `trg_hashtag_post_count` over every post that used the tag, rewriting history nothing asked to rewrite, and it could not be undone.

**Banning hides the tag, never the posts.**
The status filter belongs on hashtag surfaces only: hashtag search, trending, the search index, and the hashtag list on a post.
It must never reach the post feed, the profile listing, post search hydration, or any other query that filters posts.
A post carrying a banned tag keeps appearing everywhere it appeared before, and its caption keeps the literal `#tag` text in every case.
An implementation that adds a status predicate to a post query is wrong however it is worded, and should be rejected in review.

The two are different decisions.
Banning a term says the term should stop being a way to find things.
It does not say every post that ever used it is in breach, and a moderator that wants a particular post gone has `remove_post` for exactly that.

## Section 4: Inter-Module Dependencies

| Dependency | Direction | Nature |
|------------|-----------|--------|
| `post` | inbound | `post_hashtags` links posts to hashtags; trigger fires on `post_hashtags` insert/delete. The post module also reads `HashtagService` for the banned-name check and for the hashtag list on a post response |
| `admin` | inbound | `AdminHashtagService` owns the lifecycle endpoints and the `admin_actions` row; this module owns the table and every side effect, the same division `remove_post` uses with `post` |
| `recommendation` | inbound | `user_events` records `hashtag_click` events referencing `entity_type = 'hashtag'` |
