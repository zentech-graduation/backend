 # Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [Unreleased]

### Added
- Refresh tokens are now also issued as an `HttpOnly`, `SameSite`-scoped cookie on login, email verification, OAuth2 code exchange, and refresh, so browser clients can restore a session after a page reload without persisting a credential to web storage.
- New `app.security.refresh-cookie` configuration group controls the cookie's name, path, `Secure` flag, and `SameSite` policy per environment.

### Security
- Closed several remaining ways a blocked party's identity could leak: the live comment feed now filters each subscriber individually instead of broadcasting to everyone watching a post, notification listings and unread counts exclude blocked actors, mentioning a blocked account no longer delivers a notification, and the last few endpoints that confirmed a block's existence now respond identically to a nonexistent account instead.
- Email uniqueness is now case-insensitive, closing a duplicate-account gap equivalent to the one already closed for usernames.
- A forged pagination cursor for the conversations list could overflow the underlying timestamp column and return a server error instead of a clean validation failure; it now uses the same bounded cursor format as every other paginated list.
- A concurrent duplicate unfollow, follow-request rejection, unblock, unlike, or unsave request could return a server error instead of a not-found response; all five now resolve cleanly under concurrent requests.
- Ten paginated endpoints across posts, comments, conversations, and stories previously accepted an unbounded or zero page size; one of them could be driven to a server error this way. All paginated endpoints now enforce the same 1-100 page size bound.
- A pagination cursor obtained from one list endpoint (for example, the followers list) can no longer be replayed against a different list endpoint; cursors are now bound to the endpoint that issued them. Any cursor obtained before this change is rejected once; affected clients simply restart pagination from the first page.
- The comment and notification WebSocket endpoints now deny all cross-origin connections by default when no allowed origins are configured, matching the existing REST behavior. A blank configuration previously admitted any localhost-scoped browser origin on these two endpoints only.
- A user blocked from viewing a post could still register as a live watcher of that post's comment activity over WebSocket by sending watch or heartbeat frames directly, bypassing the same visibility check already enforced when subscribing; both frame types are now authorized identically, and an unrecognized comment-topic subscription is now rejected by default instead of allowed through.
- An endpoint reachable by more than one HTTP method (the conversations list and the WebSocket handshake) previously received a separate rate-limit budget per method instead of one shared budget; the two methods now share a single budget, and the production WebSocket handshake limit is raised from 30 to 60 attempts per 60 seconds to preserve the same effective capacity.
- A request body that is malformed, has an unrecognized field, an invalid enum value, invalid JSON, or is missing now returns 400 Bad Request with a dedicated error code instead of 500 Internal Server Error, and the response no longer echoes the rejected field name or any internal class name.

### Changed
- `POST /auth/refresh` and `POST /auth/logout` accept the refresh token from the `luvax_refresh` cookie when the request body omits it; a token supplied in the body always takes precedence.
- `POST /auth/refresh` now returns `401` rather than `400` when no refresh token is supplied by either the body or the cookie.
- `POST /auth/logout` clears the refresh cookie and remains idempotent when no token is supplied at all.
- Flyway no longer accepts out-of-order migrations; the migration set is a contiguous sequence with no gaps, so this only re-enables a safety check that was previously suppressed for no reason tied to an actual workflow.
- Notification API responses now embed the triggering user's summary (id, username, display name, avatar, verified flag) instead of a bare actor id; a soft-deleted or unknown actor now renders as a placeholder instead of a raw id the client had to resolve separately. This is a breaking change to the notification response shape.
- The example environment file now documents 22 previously-undocumented configuration variables that already had defaults, covering the refresh-token purge job, the WebSocket revocation sweep interval, the notification live-push toggle, and several module seed/consumer/scheduler toggles.

### Fixed
- Validation constraints declared on security configuration properties are now enforced at startup; they were previously bound without ever being checked.
- Login, token refresh, password reset, and OAuth2 code exchange all reject a banned, suspended, deactivated, or unverified account with 403, but none of them documented it, so a client had no documented contract to branch on. All four now declare it, including which error code corresponds to which account state.
- Every endpoint that accepts a request body now documents the 415 it returns for an unsupported content type, along with four further statuses that were reachable but undeclared (a malformed id in the path, an invalid token sent to an otherwise-anonymous endpoint, and removing a group's last admin).
- Timestamps delivered over WebSocket rendered in the server's local offset while the same field over REST rendered in UTC; both now render in UTC.
- Every notification's `isRead` field in the API response was hardcoded to false regardless of its actual read state, so a client could never tell a read notification from an unread one. It now reflects the real value.
- A rejected WebSocket handshake (missing or invalid token) returned an empty 200 response, indistinguishable from an unavailable endpoint; it now returns 401.
- The same timestamp field on the same resource rendered with a local UTC offset right after creation and a UTC (`Z`) offset on every subsequent read; every timestamp now renders in UTC regardless of which code path produced it.
- The dev environment's trusted-proxy list only recognized the IPv4 form of localhost, so `X-Forwarded-For` was silently ignored for requests arriving over IPv6 loopback, which is how most local requests actually arrive.
- A malformed feed pagination cursor was silently ignored, returning an empty page instead of an error, for a viewer who does not follow anyone yet. It is now rejected the same way regardless of how many accounts the viewer follows.
- Post and hashtag search reported another page was available whenever the current page happened to be exactly full, even on the last page, forcing an extra request that always came back empty; this now matches the already-correct behavior of user search.
- The documented 401 on the user profile lookup for a private account has been removed; the endpoint always returns 200, with counter fields null when the caller cannot see them.
- A user's, post's, or comment's `updated_at` value returned by the API could be a few milliseconds off from what was actually persisted, because both the application and the database independently computed it on every update. The database is now the sole source of truth for this value.
- Several published API responses documented the wrong status code (422 where the API actually returns 400) or omitted responses the API actually returns (401, 403, 409); documentation now matches actual behavior, and every field that can genuinely be null is now marked nullable instead of only a previously observed subset.
- Hashtag search now accepts flat query parameters instead of requiring a client to bind an object, matching its documented contract.
- A comment's timestamps could render as null immediately after creation; comment creation now returns the fully persisted values.
- Requests with an unsupported or unacceptable content type, or missing a required query parameter, now return the correct 415/406/400 response instead of the platform default error page, which no longer requires authentication to reach.
- Every successful API response in the published API documentation now declares its actual response body type instead of an untyped envelope or the wrong schema, so client code can be generated correctly from it; the logout endpoint's documented response is also corrected to match its actual empty body.
- Every cursor-paginated endpoint's published API documentation now declares the 400 response returned for a malformed cursor, and every request-body-accepting endpoint's documentation now declares the 400 response returned for a malformed body; most previously did not.
- The hourly hashtag trending snapshot job now reuses the same time window across runs within the same hour instead of creating an unbounded, ever-growing set of near-duplicate snapshot rows on every run.
- A transient failure resolving one WebSocket session's token during the periodic revocation sweep no longer aborts the whole sweep pass; the affected session is skipped and retried on the next scheduled run instead of leaving every other session unchecked.
- Closed a latent inconsistency between the case-insensitive username uniqueness constraint and the documented soft-delete retention policy; no application code path could reach the affected state, but the database now enforces the same rule the application already assumed.

### Tests
- Added regression coverage for the blocked-party disclosure fixes, the case-insensitive email constraint, the five conditional-delete race fixes, the conversation cursor bound, the page size bound on every newly-covered endpoint, the single-writer `updated_at` fix, the notification `isRead` fix, the WebSocket handshake 401 fix, and the UTC timestamp rendering fix.
- Added a contract test asserting that every request-body endpoint declares its 415, derived from the published document rather than a fixed list, so a new endpoint that omits it fails the build.
- Added regression coverage proving cursor pagination for notifications, reports, admin actions, and conversations does not drop or duplicate rows when many items share the same sort timestamp, matching coverage already in place for other paginated lists.
- Resolved a rare failure in a WebSocket revocation sweep test caused by a benign race in the test's own teardown, unrelated to the behavior under test.

### Documentation
- Recorded why a small number of harmless startup proxy warnings and one non-JSON error response for an over-long request remain as accepted, understood gaps rather than unexplained rough edges.
- Recorded that follower and following counts are visible to everyone regardless of the viewer's own blocks, so comparing a count against a filtered list can reveal that a block exists somewhere in that list, as a known and accepted tradeoff rather than an oversight.
- Corrected internal module documentation for the hashtag module, which was still marked as unimplemented scaffolding despite being fully implemented.
- Corrected internal module documentation for the message module: conversation and participant management is implemented, but sending, listing, and reacting to messages is not, since no send-message endpoint exists yet.
- Corrected internal documentation to state that account soft delete, for users specifically, is a documented but unimplemented retention policy; no code path currently sets it.
- Synchronized internal architecture documentation with the current codebase: migration count, test coverage listing, and module directory listing.
- Synchronized the reference database schema document with the two most recent migrations (the top-liked-comments index and the case-insensitive username index).
- Internal comment-style guidance no longer cites a pre-commit hook that does not exist in this repository.

### Added
- The first page of a post's comments now begins with up to three pinned top comments, ordered by like count; each comment carries a `pinned` flag so a client can tell them apart from the newest-first list rather than inferring it from position. Only comments with at least one like are eligible, a pinned comment is never repeated in the same page's newest-first body, and the pinned block is additional to the requested page size. Page two onward is unchanged.
- Notifications are now delivered in real time over a WebSocket connection, in addition to the existing REST endpoints; a client may subscribe only to its own notification stream, and a missed push is always recoverable by re-fetching the notification list.
- A WebSocket connection is now terminated automatically if the underlying account is banned, suspended, or logged out, rather than remaining open until the access token naturally expires.
- A WebSocket handshake is now rate limited, and every rejected handshake is logged.
- User search by username is available to authenticated callers, matching case-insensitively on any part of the username and ordering results by follower count.
- The users a caller has blocked can now be listed as a paginated page, newest block first.
- A user's public profile can now be fetched by username as well as by id; the username match is case-insensitive.

### Removed
- The development-only feed seed data script is no longer part of the application; local development databases no longer receive this seed data automatically.

### Changed
- Usernames now identify an account case-insensitively while preserving the casing they were registered with. `Alice` and `alice` are the same person, so only one of them can exist, and logging in or looking up a profile works with any casing. The profile continues to display the casing the account was created with rather than a lowercased form.
- Registering or renaming to a username that differs from an existing one only by case is now rejected with the same generic conflict returned for any other duplicate. Previously it slipped past the availability check and surfaced as a different, more specific error, which allowed a caller to distinguish a taken username from a taken email.
- Notification push delivery latency is significantly reduced by polling for new events roughly five times more often.
- A comment or story WebSocket session established before an account is banned, suspended, or logged out is no longer left open until its access token naturally expires; the session is now terminated shortly after the account status changes.
- The user object returned by login, register, and refresh is renamed in the API schema from `UserSummaryResponse` to `AuthenticatedUserResponse` to distinguish the authenticated-self object (which carries email and role) from the shared public author summary; the emitted JSON fields are unchanged.
- The follower, following, and pending follow-request lists now use the shared user summary object; the emitted JSON is unchanged, only the shared shape is reused.
- Post responses (single post, feed, saved posts, and a user's posts) now embed the author as a nested user summary object (id, username, display name, avatar URL, verified flag) instead of separate top-level author id, username, display-name, and avatar fields; the post likers endpoint now returns that same user summary shape, and a post caption edit history entry embeds the editor the same way instead of a bare editor id. A post by a deleted author is hidden as before; a deleted liker now appears as a placeholder rather than silently vanishing from the likers list.
- Comment responses now embed the author as a nested user summary object (id, username, display name, avatar URL, verified flag) instead of a bare author id; the previous top-level `userId` field is removed, and a comment by a deleted author returns a placeholder author rather than a dangling id. The same author object arrives over the live comment WebSocket feed, so a live-rendered comment shows the same author as one fetched over REST.
- Pagination cursors are now opaque and share a single format across every list endpoint; cursors issued by a previous version are no longer accepted.
- The report and admin listing endpoints now name their page-size parameter `limit`, matching every other paginated endpoint.
- The notifications endpoint maximum page size is raised from 50 to 100.
- A malformed pagination cursor now returns a 400 error with a typed code instead of silently returning the first page.
- Post search now rejects a cursor addressing a result offset beyond 10,000 with a 400 error, matching the bound hashtag search already applied; previously only a negative offset was rejected.
- A comment WebSocket handshake rejected because of a blacklisted (logged-out) token now returns the same response as every other rejection reason, with no distinguishing status code.
- The social module's internal route constants now match the endpoints they describe; no endpoint path changed.
- Removed unused internal path constants that described endpoints the application never served; no served endpoint changed.

### Added
- A shared public user summary object - user id, username, display name, avatar URL, and verified flag - is now available for embedding an author or actor inline in API responses; a soft-deleted or unknown user resolves to a placeholder rather than a missing value.
- Foundation for the direct messaging module: 1-1 and group conversations, group participant management (add, remove, leave with automatic admin handoff), group renaming, and cursor-paginated conversation listing with per-conversation unread counts.
- A message request from a user who is blocked, or from a non-follower when the recipient has disabled message requests, is now rejected.
- Users can now log in with either their email address or their username, supplied through a single login identifier field.

### Changed
- The login endpoint now accepts a single identifier field containing an email address or a username in place of the previous email-only field; this is a breaking change to the login request contract.

### Fixed
- A comment delivered over the live WebSocket feed, or served from the recent-comments cache, no longer includes a "liked by viewer" flag; that flag is meaningful only per-viewer and could previously show one viewer's own like state to every other subscriber of the same post. It remains present and correct on every REST response.
- Post list endpoints (feed and a user's posts) no longer issue one extra media query per post; the media for a whole page is now loaded in a single batch.
- Cursor-paginated lists no longer drop items that share an exact creation timestamp when paging across the boundary; feeds, profile posts, likes, saves, comments, replies, followers, and following now return every item exactly once.
- An exactly-full final page of any cursor-paginated list now correctly reports that no further page exists rather than advertising a next page that is empty.
- A banned or suspended account can no longer complete the comment WebSocket handshake; a still-valid token now authenticates only when the account's status is active, matching the guarantee already enforced on REST requests. An already-open connection from before the status change is now also terminated shortly after, rather than remaining open until its token naturally expires (see Changed).

### Tests
- Regression coverage proving a user cannot subscribe to another user's notification WebSocket topic, including from a session established on the comment WebSocket endpoint.
- End-to-end coverage proving a notification is delivered to the correct recipient's WebSocket session only, is suppressed correctly (self-action, disabled preference, blocked actor), and is not delivered to an unrelated connected user.
- Regression coverage proving a WebSocket session is terminated shortly after the underlying account is banned, suspended, or its token is logged out, and that a still-valid session survives the same check.
- Regression coverage proving repeated WebSocket handshake attempts beyond the configured limit are rejected.
- Regression coverage reproducing keyset row loss on a group of rows sharing one creation timestamp and proving the tuple-cursor fix returns every row exactly once.
- Regression coverage asserting a banned or suspended account's otherwise-valid token no longer establishes a comment WebSocket session.
- Regression coverage proving the comment WebSocket handshake rejects a missing, malformed, wrong-secret, expired, or blacklisted token, and rejects an unauthenticated caller on the SockJS HTTP fallback transport as well as the native transport.
- Integration tests that start a Redis container now pin an explicit blank password so a developer's local `REDIS_PASSWORD` environment variable can no longer leak into the test context and cause unrelated authentication failures.
- End-to-end coverage connecting a real STOMP client through the full security filter chain and asserting a live comment event is received.
- Regression coverage asserting the trending hashtags endpoint rejects an oversized page size and a negative page number.
- Regression coverage asserting the social module's route constants resolve to the paths it actually serves.
- Regression coverage asserting the removed unused path constants no longer exist.
- Regression coverage for concurrent attempts to start a 1-1 conversation with the same user, asserting exactly one conversation results.
- Regression coverage for conversation-list pagination across conversations with no messages yet.
- Regression coverage for group-admin handoff when the last admin is forcibly removed from a group.
- Unit coverage for conversation creation and deduplication, participant and group-admin gating, group admin handoff on leave, and conversation-list cursor pagination.
- End-to-end integration coverage for the new conversation endpoints, covering creation, deduplication, listing, group management, and membership changes.
- Coverage for login by username and by email through the identifier field, including uppercase-username resolution and identical failure responses for an unknown identifier and a wrong password.

### Fixed
- Comment and story activity (new comments, replies, mentions, comment likes, and story views) now generates notifications in production; these notification types were previously never created outside the development environment because their event consumers were not enabled.
- Conversation creation, detail, and list responses now correctly report whether a conversation is a group instead of always reporting false.
- Removing the last active admin from a group conversation (including the admin removing themselves) now automatically promotes a replacement admin, matching the existing behavior when an admin leaves voluntarily.
- Paginating the conversation list past a conversation with no messages yet no longer returns a 500 error.
- Out-of-range request parameters (such as an oversized page size or limit) on the notification, moderation-action, and report listing endpoints now return 400 Bad Request instead of 500 Internal Server Error.
- A reply can no longer be attached to a parent comment that belongs to a different post; such requests are now rejected as not found and no longer corrupt reply or comment counters.

### Security
- The live comment WebSocket connection can now actually be established; it previously rejected every real client because the browser-only query-parameter authentication path was never permitted through the access control rules.
- The trending hashtags endpoint now rejects a page size above 100 or a negative page number with 400 Bad Request instead of accepting an unbounded page size, and no longer accepts a client-supplied sort field.
- Two concurrent requests to start a 1-1 conversation with the same user can no longer create duplicate conversations; the request is now serialized and backed by a database uniqueness constraint.
- The follower and following list endpoints now reject an out-of-range limit or an oversized cursor with 400 instead of silently clamping the value.
- The resend email verification endpoint now normalizes its response time to a floor, so a registered address can no longer be distinguished from an unregistered one by response latency.
- Duplicate reports for the same reporter, target, and type are now prevented by a database uniqueness constraint, closing a race in which two concurrent submissions could both be recorded.
- Username availability on profile update is now checked across all accounts including soft-deleted ones, so a collision with a soft-deleted account returns the same conflict response as an active one and no longer reveals account state.
- Post search now bounds its page size, so an oversized request can no longer force a mass result hydration or exceed the search engine's result-window limit.
- Post creation now bounds the number of media identifiers accepted, rejecting an oversized list at request validation.
- Moderation action metadata is now bounded to a maximum number of entries, preventing unbounded growth of the moderation audit table.
- Usernames are now stored and matched case-insensitively, and the login rate limit now keys on the submitted identifier so username-based attempts are throttled per account rather than falling back to an address-only bucket.

### Removed
- Unused internal mail request type.

### Fixed
- The local PostgreSQL container now accepts the legacy IANA timezone name `Asia/Saigon`, which some database clients (for example DBeaver) send by default, resolving a connection failure for users in that timezone.

### Added
- Deduplicated story view recording, with the trigger-maintained view count returned on every call and owner views exempted from counting.
- Owner-only cursor-paginated list of a story's viewers, newest view first.
- A `story_view` notification is created for the story owner on a viewer's first view of a story.
- Scheduled hourly cleanup that hard-deletes stories once they are both soft-deleted and past expiry.

### Tests
- Story view recording and viewer-list unit coverage, notification-consumer unit and integration coverage, and integration coverage for view deduplication, the outbox event, the viewer list, and the cleanup job's selectivity.

### Added
- Story creation backed by an owned media asset, with story type and 24-hour expiry derived automatically.
- Story reads: a single story, a user's active stories, and a feed tray grouped by author with unseen-first ordering.
- Owner-only soft delete of a story.
- Story visibility enforcement: private accounts require an accepted follow, and blocks hide stories in both directions.

### Tests
- Story lifecycle and visibility service unit coverage, and an end-to-end integration test covering creation, feed, per-user listing, deletion, media-ownership rejection, expiry, private-account gating, and blocking.

### Changed
- STRUCT.md updated to reflect `comment` module as fully implemented, adding sub-package list, module responsibility, test coverage rows, `comment.live.events` fanout exchange, `comment.notification.queue` with its DLQ, and three binding rows.
- STRUCT.md updated to reflect `report` and `admin` modules as fully implemented, including accurate sub-package lists, module responsibility descriptions, test coverage rows, and Flyway migration count corrected to 29 (V28 add_user_events_upcoming_partitions and V29 preserve_admin_action_audit_history added).
- `docs/modules/report/DATA_RULES.md` implementation status updated from scaffolding-only to fully implemented; all business rule entries annotated with the service methods that now enforce them.

### Fixed
- Concurrent duplicate follow requests now resolve atomically: the losing request receives a clean already-following/already-requested error instead of a generic conflict, and duplicate follow notifications are no longer emitted.
- Hashtag names longer than 100 characters are now dropped during normalization instead of failing the whole post publish or caption update with a misleading conflict error.
- Malformed path or query parameter values (for example a non-UUID user ID) now return 400 Bad Request instead of 500, and no longer write attacker-controllable stack traces to the error log.

### Security
- Media upload URLs now bind the client-declared content length into the presigned signature, so an oversized body can no longer be uploaded past the maximum media size limit; clients must send a matching `Content-Length` header on upload.
- Unlike and unsave now mask hidden (draft or archived) posts as not-found for non-owners, closing an existence oracle that revealed the lifecycle state of other users' unpublished posts, and reject unlike/unsave on a published post that is blocked, private without an accepted follow, or owned by a deactivated account.
- Visibility-gate rejections on like, unlike, save, and unsave now return `POST_NOT_FOUND` uniformly instead of `POST_FORBIDDEN`, preventing callers from distinguishing a hidden post from a nonexistent one by error code.
- Notification list cursor lookups are now scoped to the authenticated recipient, removing a cross-user oracle that disclosed whether an arbitrary notification ID exists and when it was created.
- Comment like and unlike now enforce the parent post's visibility rules, closing a gap that let blocked or non-follower users like comments on posts they cannot view.
- OAuth2 sign-in now requires the identity provider to assert the email as verified before creating a new local account, preventing account squatting on an unverified email address.
- Removed the email address from JWT access-token claims so account email is no longer readable from the unencrypted token payload.
- Public user-profile lookups now enforce block and private-account visibility: a block in either direction returns 404, and follower/following/post counts are hidden from non-followers of a private account.
- Post captions and user bio are now length-bounded at the request layer (2200 and 500 characters respectively).

### Fixed
- Preserved moderation audit history when an acting administrator is permanently deleted by changing the actor foreign key to `ON DELETE SET NULL`.
- Synchronized `database/schema.sql` with Flyway V29 so new environments preserve audit rows after an acting administrator is deleted.
- Concurrent duplicate like or save on a post, and duplicate like on a comment, now return a clean 409 conflict instead of a 500.
### Added
- Admin moderation workflows for user status, post and comment removal/restoration, report closure, and cursor-paginated immutable audit history.
- Split admin moderation into 13 explicit API operations and added user-specific audit history, summary DTO mapping, append-only repository verification, and transactional rollback coverage.
- OpenAPI-documented admin endpoints with moderator/administrator role enforcement and structured audit metadata.
- Admin unit and full-stack integration coverage for authorization, target mutations, audit queries, and actor-deletion retention.
- Automated `user_events` monthly partition management: a migration backfills the current and next two months' partitions, and a scheduled job pre-creates the partition two months ahead each month so new events no longer accumulate in the default catch-all partition.

### Security
- Development seed data is no longer applied in production: Flyway now scans `classpath:db/migration` only by default, the dev seed moved to a dev-profile-only `classpath:db/dev-seed` location.

### Fixed
- Resolved Flyway duplicate-version conflict on V25: renamed `V25__add_comment_moderation_status.sql` to V26 and `V26__create_comment_write_idempotency.sql` to V27, restoring application startup and unblocking all 144 integration-test errors.
- Per-instance live-comment RabbitMQ queue now declares durable instead of non-durable, resolving a broker-rejected `queue.declare` (`transient_nonexcl_queues` deprecation on RabbitMQ 4.x) that crash-looped the live-comment consumer connection on startup.

### Added
- Report submission and moderation workflow with polymorphic target validation, duplicate and self-report prevention, role-restricted retrieval, and controlled status transitions.
- Report pending moderation endpoint returning FIFO cursor-paginated queue summaries.
- Unit and Testcontainers integration coverage for report submission, validation, authorization, retrieval, and status transitions.
- Comment module: create, edit, soft-delete (subtree), likes, and nested replies up to depth 10, with cursor-paginated listing of top-level comments and replies.
- Synchronous comment moderation (content normalization plus rule-based rejection for empty, over-length, blocked-word, and spam content).
- HTTP write idempotency for comment creation via an `Idempotency-Key` header, replaying the original response on a matching retry and rejecting key reuse with a different payload.
- Optional per-post comment slow-mode rate limiting backed by Redis.
- Event-driven comment, reply, mention, and like notifications via a dedicated RabbitMQ consumer.
- Redis recent-comments cache with cache-aside writes, rebuild-on-miss, and fail-open degradation.
- Real-time comment delivery over WebSocket (STOMP/SockJS) with JWT-authenticated handshake, per-subscription post-visibility authorization, and per-instance RabbitMQ fanout queues.
- Comment subsystem metrics and a Redis/RabbitMQ health indicator.

### Changed
- Report cursor pagination now uses explicit query limits without Spring Data offset pagination abstractions.
- Report moderation list endpoints now return cursor-paginated `ReportSummaryResponse` pages instead of offset-paginated full report details.
- `ApiErrorCode` gains comment-scoped error codes.
- RabbitMQ topology gains the `comment.live.events` fanout exchange (bound to the event bus for `comment.#`) and a dedicated `comment.notification.queue` with its dead-letter queue.
- Comment write and live-delivery paths emit MDC correlation fields (commentId, postId, userId, eventId, serverId).

### Fixed
- Report response OpenAPI schemas now include examples and required-field metadata.
- Comment list endpoints now enforce post visibility; private posts return 403 to non-followers, consistent with the create and WebSocket paths.
- Idempotency key races are resolved with an `INSERT ... ON CONFLICT DO NOTHING` reservation in the request transaction, so a concurrent duplicate cannot poison the transaction and a rolled-back create frees the key.
- WebSocket handshake now rejects blacklisted (revoked but unexpired) access tokens.
- Cursor pagination `hasNextPage` is computed from the pre-trim probe row, fixing a false positive on an exactly-full final page.
- Text-only post type: posts with `postType: text` require a non-blank caption and no media attachments.
- Structured logging for outbox publish failures, pre-signed media upload URL failures, user registration and login outcomes, and post creation/status-transition events.
- Chronological following feed endpoint (`GET /api/v1/posts/feed`): cursor-paginated published posts from accepted-follow accounts, newest first, with bidirectional block exclusion and empty-following short circuit.
- `V99__seed_feed_test_data.sql` Flyway migration seeding 7 users, 5 follows, 2 blocks, and 12 posts covering all feed business rules for manual endpoint testing.

### Changed
- Disabled raw per-statement SQL logging (`show-sql`, `format_sql`, `use_sql_comments`) in the dev profile in favor of the new structured application logs; the prod profile already had these disabled and needed no change.
- Synced `database/schema.sql` with all 24 Flyway migrations (V01–V24): added missing V18 metadata config tables (`system_settings`, `notification_type_configs`, `moderation_action_configs`, `feature_flags`, `report_reason_configs`) with seed data and triggers; V23/V24 were already reflected. Updated struct.md migration count from 22 to 24.

### Fixed
- Prevented an unexpected login-request body read failure from escaping the security filter chain uncaught, which previously bypassed the standard API response envelope entirely.
- Corrected `PostMapper.toResponse` and `PostMapper.toFeedResponse` to map the pre-assembled `media` parameter instead of `post.media`; the wrong source caused MapStruct to generate code that discarded the CDN URL, media type, dimensions, and blurhash from all post API responses.
- Prevented a broken message-broker channel from letting an acknowledgment failure escape the auth mail, post index, hashtag index, and social notification event consumers uncaught.
- Logged outbox publish failures with the original exception instead of only persisting a truncated error message to the database, restoring log-based visibility into async event delivery outages.
- Logged pre-signed media upload URL failures instead of silently discarding the underlying storage-provider exception.
- Added author `username`, `displayName`, and `avatarUrl` to post and feed API responses, batched via a single author lookup alongside the existing media batching, fixing a frontend crash on the main feed caused by missing author display fields.
- Replaced `ResultSetExtractor` lambda with `queryForObject` in `HashtagTrendingServiceImpl` to resolve always-false null check on latest trending period (SonarQube `java:S2583`).
- Added emptiness guards before `doesNotContainAnyElementsOf` assertion in `HashtagControllerIT` to prevent trivially passing pagination test (SonarQube `java:S5841`).

### Security
- Removed the recipient email address from transactional email send success/failure log lines to eliminate a PII exposure.
- Logged every JWT authentication rejection reason so failed-auth attempts are no longer indistinguishable from one another in application logs.
- Logged refresh-token replay/theft-detection events before revoking all active sessions for the affected user, so a real token-theft incident is no longer silent.
- Logged OAuth2 state-cookie signature and deserialization failures instead of silently treating a tampered or corrupted cookie as an absent authorization request.

### Tests
- Added authorization coverage for regular users accessing the pending report queue and report status updates.
- Added WebSocket JWT handshake interceptor unit tests covering valid non-blacklisted token, revoked (blacklisted) token, missing token parameter, and invalid token paths.
- Added pagination boundary unit tests verifying `hasNextPage=false` on an exactly-full final page and `hasNextPage=true` with one probe row beyond the limit.
- Added comment-module coverage for the idempotency replay and conflict paths (unit + controller IT), admin-delete authorization, read-side visibility on private posts, and the reply/like/mention notification consumer branches.
- Added `SocialControllerIT` covering all 7 REST endpoints of the social module (follow, unfollow, block, unblock, get followers, get following, follow-request list, approve, reject) against real PostgreSQL and Redis containers.
- Extended `AuthMailEventHandlerTest` with 11 new methods covering null eventId, unsupported event type, wrong aggregate type, missing/null/non-String/non-UUID data.userId, inactive user, displayName fallback, and all remaining dispatch branches.
- Extended `AuthMailEventConsumerTest` with 4 new `isTransient` methods covering `DataAccessException`, `RedisSystemException`, `AmqpException`, and non-SERVICE_UNAVAILABLE `AppException` inputs.
- Extended `MediaMetadataValidatorTest` with 8 new methods covering blank storage key, leading/trailing slashes, zero max-size system setting, zero width/height, negative video duration, blank blurhash, and zero file size.
- Extended `OutboxPublisherServiceImplTest` with 5 new methods covering `ExecutionException` on confirm, interrupt on confirm, returned message marking event failed, null exception message default text, and `markPublished` returning false without throwing.
- Extended `DeadLetterPublisherTest` with 3 new methods covering `ExecutionException`, message returned from broker, and nack from broker.
- Extended `HashtagTrendingServiceImplTest` with 3 new methods covering no-period sentinel (empty result), single hashtag, and multi-hashtag result set.
- Added `CustomOidcUserTest` with 5 methods covering all delegated methods of `CustomOidcUser`.
- Added unit test coverage for previously untested service classes, targeting failure and exception branches: the social service (self-follow, not-found, block, already-following/requested, follow-request approve/reject, block cascade purge, private-account visibility, cursor decoding), the OAuth2 exchange-code service (absent-code rejection), the system-setting accessor (missing and non-numeric values), the post search fallback (availability degradation vs. programming-error rethrow), the post response assembler (batched media hydration), and the hashtag trending empty-snapshot guard.
- Integration tests for the hashtag and post index-sync consumers covering at-least-once delivery, idempotent reprocessing, dead-letter routing of malformed messages, and the post out-of-order delete-before-upsert gate, against real PostgreSQL, Redis, RabbitMQ, and Elasticsearch containers.
- `PostControllerIT` search scenario now drives the real outbox to RabbitMQ to consumer to Elasticsearch path instead of seeding the index directly.
- Unit tests for the post service (creation media validation, carousel cardinality, owner-only authorization, lifecycle transitions, soft-delete invariants, hashtag extraction), post visibility service, and the like and save services (idempotency and visibility enforcement).
- Integration test covering post CRUD, lifecycle, visibility gating across public, private, and blocked combinations, like and save idempotency, and search behaviour with Elasticsearch available and stopped.

### Changed
- Hashtag Elasticsearch propagation moved from a synchronous post-commit dual-write to outbox-based asynchronous publishing, emitting an index upsert or delete event per affected hashtag after the post-association change is flushed.
- Post Elasticsearch index is now maintained through the outbox on publish, caption update, archive, and soft-delete; publish and unarchive upsert the document while archive and soft-delete remove it.
- `STRUCT.md` (`.claude/rules/` and `.agents/rules/`) updated to reflect current codebase state: 21 Flyway migrations (V01–V21), 7 implemented modules (auth, mail, users, social, media, hashtag, notification), new `common/` packages (inbox, outbox, messaging, settings, config/elasticsearch, config/rabbit, config/security), restructured `security/` sub-packages, Elasticsearch service and config, full RabbitMQ topology, updated Technology Stack versions, and accurate Redis key patterns.

### Fixed
- `SocialServiceImpl.decodeCursor` returned `null` for the first-page request, which was passed as an untyped JDBC parameter to JPQL causing `PSQLException: could not determine data type of parameter` under `stringtype=unspecified`; `decodeCursor` now returns a far-future sentinel (`now + 100 years`) and the JPQL `IS NULL OR` guard is removed since the sentinel makes it redundant.
- Logout now propagates token-blacklist failures instead of swallowing them, and revokes the refresh token before blacklisting so a blacklist failure cannot leave both invalidations un-applied.
- Added forward migration V23 to drop the legacy plaintext `access_token`, `refresh_token`, and `token_expires_at` columns from `oauth_accounts` (idempotent `DROP COLUMN IF EXISTS`), reconciling databases that ran the original migration with the rewritten one; the prior changelog and schema attribution of this drop to V19 was incorrect.
- Index-sync consumers now inject the Spring Boot 4 (Jackson 3) `ObjectMapper`; the prior Jackson 2 type had no registered bean and failed Spring context startup.
- Duplicate `app.hashtag` and `app.post` keys in `application.yaml`, which broke YAML parsing and prevented every dev-profile Spring context from loading, are merged into single blocks.
- New posts are flushed before the index-upsert event reads the database-generated creation timestamp, preventing a null-timestamp failure on the publish path.
- Index event payloads deserialize correctly when the optional schema `version` field is absent from the message.
- `Follow` entity no longer maps a non-existent `deleted_at` column; `FollowRepository` JPQL queries and derived method names that referenced `deletedAt` are updated to match the actual schema.
- `SocialNotificationConsumer` now activates in dev and prod profiles via `app.notification.consumer.enabled: true`; `application.yaml` wires the property from `NOTIFICATION_CONSUMER_ENABLED` with a `false` default.
- `NotificationControllerIT` JWT construction replaced with `JwtTokenProvider.generateAccessToken()` and the `@DynamicPropertySource` block now overrides `spring.data.redis.password` to prevent the `.env`-sourced password from being sent to the password-less test Redis container.
- Hashtag entities now delegate `created_at` to the PostgreSQL `DEFAULT NOW()` column default instead of generating the timestamp on the JVM, aligning with the documented timestamp policy.
- Hashtag search circuit-breaker fallback now rethrows non-availability errors instead of masking programming and data errors as Elasticsearch degradation, and logs the full exception with its stack trace.
- Hashtag search now pages the Elasticsearch query by the exact cursor offset rather than integer-dividing the offset into a page index, eliminating silently skipped or repeated results when the requested page size varies between requests.
- Hashtag name normalization returns an empty string for null or blank input instead of throwing, so a null tag in a post's tag list is dropped rather than aborting the upsert transaction.
- Hashtag id is now generated by the PostgreSQL `gen_random_uuid()` column default instead of the JPA `AUTO` strategy, aligning with the documented ID policy.
- Hashtag Elasticsearch repository no longer connects to Elasticsearch during application-context startup (`@Document(createIndex = false)`); the index and its ngram mapping are created by the seed runner when Elasticsearch is reachable. This prevents every Spring context without an Elasticsearch instance from failing to start.
- Redis connection now authenticates correctly when `REDIS_PASSWORD` is set, resolving NOAUTH errors on startup.

### Added
- RabbitMQ topology for Elasticsearch index synchronization: durable `hashtag.index.sync` and `post.index.sync` work queues with dead-letter queues, bound to the shared `social.events` topic exchange with dead-lettering to `social.events.dlx`.
- Idempotent `hashtag` and `post` index-sync consumers that apply Elasticsearch upsert and delete events with bounded in-process retry and dead-letter routing on permanent or exhausted failures; the post consumer re-checks PostgreSQL and indexes only existing published posts, dropping stale out-of-order events.
- Post creation, retrieval, caption update, lifecycle transition, and soft-delete endpoints, with per-post append-only caption edit history readable by the owner.
- Post like and save endpoints with idempotent semantics and cursor-paginated liker and saved-post listings.
- Post visibility enforcement gating retrieval, listing, liking, and saving by block relationships and private-account follow state.
- Elasticsearch full-text post search guarded by the `elasticsearchSearch` circuit breaker, degrading to an empty result page when the search tier is unavailable.
- Posts Elasticsearch index seed runner that batch-loads published posts from PostgreSQL on startup only when the index is empty and `app.post.seed.enabled` is true.
- `post_edit_history` table (Flyway V22) recording the pre-edit caption and editor for every caption change, append-only and never soft-deleted.
- Follow and block state read methods on the social service exposing accepted-follow and bidirectional-block checks to other modules.
- Hashtag-id lookup on the hashtag service returning hashtag associations grouped by post for index seeding.
- Post API error codes `POST_NOT_FOUND`, `POST_FORBIDDEN`, `POST_ALREADY_LIKED`, and `POST_ALREADY_SAVED`, and post API path constants for status, history, likes, saved, and search.
- Public hashtag HTTP endpoints `GET /api/v1/hashtags/search` and `GET /api/v1/hashtags/trending`, documented via the `HashtagApi` OpenAPI interface and rate-limited per endpoint.
- `HashtagIndexSeedRunner` seeding the Elasticsearch `hashtags` index from PostgreSQL on startup only when the index is empty and `app.hashtag.seed.enabled` is true, treating seeding failures as non-fatal so startup never blocks on the rebuildable search tier.
- `HashtagTrendingService` computing ranked hashtag trending snapshots from a windowed `post_hashtags` aggregation and serving the latest snapshot as an offset-paginated response, driven by a scheduled, transactional snapshot job.
- `HashtagSearchService` providing fuzzy hashtag name search backed by an Elasticsearch ngram match with automatic degradation to a PostgreSQL `pg_trgm` query, guarded by the `elasticsearchSearch` circuit breaker and returning cursor-paginated results.
- `HashtagService` with normalization, idempotent post-hashtag association upsert, and a synchronous post-commit Elasticsearch dual-write that never rolls back the source-of-truth write on indexing failure; `hashtags.post_count` remains trigger-owned.
- Hashtag module scaffolding: `/search` API path constant, `HASHTAG_NOT_FOUND` error code, `app.hashtag` configuration namespace, and a `HashtagProperties` bean binding the trending-job and seed settings.
- Hashtag module API DTOs (`HashtagResponse`, `HashtagTrendingResponse`, `HashtagSearchRequest`) and a MapStruct `HashtagMapper` converting between entities, the Elasticsearch document, and response DTOs.
- Hashtag Elasticsearch projection: `HashtagDocument` (`hashtags` index) with an ngram-analyzed `name` sub-field for prefix/fuzzy search alongside a keyword main field, its `hashtags.json` index settings, and a `HashtagSearchRepository`.
- Hashtag module persistence layer: `Hashtag`, `PostHashtag`, and `HashtagTrending` JPA entities (with composite-key embeddables for the junction and trending tables) plus their Spring Data repositories, including a pg_trgm fuzzy hashtag search query and a bulk post-association deletion query.
- Elasticsearch service added to `docker-compose.yaml` using image `9.0.3` (upgraded from initial 8.17.3 to align with `elasticsearch-java:9.2.8` used by Spring Data Elasticsearch 6.x; single-node, security disabled, 512 MB JVM heap, named volume for index persistence).
- `spring-boot-starter-data-elasticsearch` dependency added to `pom.xml`; version resolved by Spring Boot BOM.
- `ElasticsearchProperties` configuration-properties bean binding `app.elasticsearch.*` (URIs, optional credentials, connect/socket timeouts).
- `ElasticsearchConfig` wires the Spring Data Elasticsearch client from `ElasticsearchProperties`; basic auth applied only when both username and password are non-blank.
- `app.elasticsearch` namespace added to `application.yaml` with environment-variable placeholders.
- `management.health.elasticsearch.enabled` and `management.endpoint.health.show-details` added to `application.yaml`; Spring Boot's auto-configured `elasticsearchHealthIndicator` handles cluster health reporting.
- `elasticsearchSearch` Resilience4j circuit breaker instance defined in both dev and prod profiles for use by future hashtag and post search services.
- Elasticsearch environment variable placeholders added to `.env.example`.

### Tests
- Added `HashtagControllerIT` integration test covering search validation, empty trending snapshots, Elasticsearch ngram search, cursor pagination, and the Elasticsearch-down pg_trgm fallback against real Postgres, Redis, and Elasticsearch containers.
- Added unit tests for `HashtagSearchService` covering the Elasticsearch ngram primary path, the PostgreSQL `pg_trgm` fallback, and empty-result pagination.
- Added unit tests for `HashtagService` covering name normalization, case-insensitive deduplication, post-commit Elasticsearch dual-write failure isolation, and post-association removal.
- Notification module: `Notification` entity, `NotificationRepository`, `NotificationService` interface and implementation, `NotificationController`, and `NotificationApi` OpenAPI interface.
- Cursor-paginated notification listing endpoint (`GET /api/v1/notifications`) returning the authenticated user's notifications in reverse-chronological order.
- Mark-as-read endpoint (`PATCH /api/v1/notifications/{notificationId}/read`) with recipient-ownership enforcement, and mark-all-as-read endpoint (`PATCH /api/v1/notifications/read-all`).
- Unread notification count endpoint (`GET /api/v1/notifications/unread-count`).
- `SocialNotificationConsumer` consuming `user.followed.v1` and `user.follow-requested.v1` events from the notification queue (gated by `app.notification.consumer.enabled`) and creating follow and follow-request notifications idempotently with bounded transient retry and dead-letter routing.
- RabbitMQ topology: `notification.queue` and `notification.dlq` declared in `RabbitMqTopologyConfig`; `NotificationRabbitBindingConfig` binds `user.followed.v1` and `user.follow-requested.v1` social events to the notification queue.
- Notification creation guards: self-notification suppression, per-type user notification-preference check (`notifyLikes`, `notifyComments`, `notifyFollows`, `notifyMentions`, `notifyMessages`), and blocked-actor check to prevent unwanted notifications.
- Elasticsearch service added to `docker-compose.yaml` using image `9.0.3`; single-node, security disabled, 512 MB JVM heap, named volume for index persistence.
- `spring-boot-starter-data-elasticsearch` dependency added; `ElasticsearchProperties` configuration bean and `ElasticsearchConfig` wiring the Spring Data Elasticsearch client from those properties.
- `elasticsearchSearch` Resilience4j circuit breaker instance defined in both dev and prod profiles for use by future search services.

### Fixed
- Notification creation now checks the governing user-settings toggle for every notification type (likes, comments, mentions, and messages in addition to follows); story views have no toggle and are never preference-suppressed.
- Fixed `SocialNotificationConsumer` failing to start when `app.notification.consumer.enabled=true` due to missing `@Autowired` on the primary constructor.
- Disabled Apache HC5 automatic-retry behavior on the `TestRestTemplate` used by `AuthControllerIT`; `httpclient5` (added transitively by `spring-boot-starter-data-elasticsearch`) was causing `Retry-After`-honoring retries on rate-limit 429 responses, making the integration test suite hang indefinitely.
- Corrected the RabbitMQ auto-configuration exclusion class name in two integration test contexts from the stale Spring Boot 3.x path to the Spring Boot 4.x path, preventing infinite RabbitMQ reconnection loops when no broker is available during test runs.

### Changed
- Authenticated notification OpenAPI operations now inherit the global bearer-auth requirement instead of re-declaring it per operation.
- Testing rules now exempt integration tests that exercise a real RabbitMQ broker via Testcontainers from the AMQP auto-configuration exclusion requirement.

### Tests
- Renamed notification and RabbitMQ topology test methods to the `{method}_{condition}_{outcome}` naming convention.
- `NotificationServiceImplTest`: 15 unit tests covering all creation guards, mark-as-read ownership and idempotency, mark-all-as-read, unread count, and cursor pagination.
- `NotificationControllerIT`: 8 integration tests covering pagination, ownership enforcement, bulk read, unread count accuracy, and JWT authentication.
- `SocialNotificationConsumerIT`: 6 integration tests covering happy path follow and follow-request creation, event-id deduplication, malformed-payload dead-lettering, self-follow suppression, and unknown-event-type skipping.
- Added unit tests for `ElasticsearchConfig` credential and timeout wiring.
- Added integration test verifying Elasticsearch cluster connectivity and actuator health status via Testcontainers.

### Fixed
- Registration now rejects an email or username that belongs to a soft-deleted account with a 409 domain error instead of propagating a database unique-constraint violation as a 500.
- OAuth2 sign-in no longer attempts to create a new account when the provider email matches a soft-deleted user; a 409 domain error is returned instead.
- Username generation for new OAuth2 users now checks the full `users` table (not just non-deleted rows), consistent with the table-wide `UNIQUE` constraint on `users.username`.

### Added
- `UserRepository` exposes table-wide `existsByEmail`, `existsByUsername`, and `findByEmail` methods aligned with the database `UNIQUE` constraints that have no soft-delete partial index.
- `POST /api/v1/auth/oauth2/exchange` back-channel endpoint: redeems a one-time opaque exchange code for a standard access/refresh token pair; rate-limited via `lowTraffic` Resilience4j instance and Redis sliding-window filter.
- `OAuth2ExchangeCodeService` with a Redis-backed implementation that stores 32-byte hex exchange codes under `auth:oauth2:exchange:{code}` (TTL 120 s) and consumes them atomically via a GET-then-DEL Lua script.
- `OAuth2ExchangeRequest` DTO with `@Schema` annotations for the new exchange endpoint.
- `AUTH_OAUTH2_EXCHANGE_CODE_INVALID` error code (HTTP 400) returned when an exchange code is absent or expired.

### Changed
- `OAuth2AuthenticationSuccessHandler` no longer writes tokens into the callback response body; instead generates an exchange code and redirects the browser to `{frontendBaseUrl}/oauth2/callback?code={code}`, eliminating token exposure in the browser redirect (resolves AUTH-012).
- `AuthService` extended with `exchangeOAuth2Code` to support the new back-channel exchange flow.

### Security
- Resolved AUTH-012 (CWE-598, MEDIUM): OAuth2 tokens are no longer delivered through the browser redirect. The success handler now issues a short-lived opaque exchange code and completes the handshake via the authenticated back-channel `POST /api/v1/auth/oauth2/exchange` endpoint.

### Added
- Users module: `UserService`, `UserController`, and `UserApi` implementing profile view and update, public profile lookup with private-account enforcement, and settings view and update endpoints.

### Documentation
- `UserProfileResponse` and `PublicUserProfileResponse` Javadoc and `@Schema` descriptions now explicitly distinguish `isVerified` (administrator-granted platform badge, `users.is_verified`, always `false` for regular users) from email confirmation status (`user_credentials.email_verified`, exposed as `emailVerified` in the auth response). Investigation confirmed `isVerified` returning `false` after email verification is correct behavior — the two fields are unrelated.
- Users module: `User` and `UserSettings` JPA entities, `UserRepository` and `UserSettingsRepository` moved from the auth module to `com.app.modules.users`.
- Users module: `UserRole`, `UserStatus` enums and their JPA converters moved from the auth module to `com.app.modules.users`.
- `GET /api/v1/users/me` — returns the authenticated user's full profile.
- `PATCH /api/v1/users/me` — partial profile update with username uniqueness enforcement.
- `GET /api/v1/users/{userId}` — public profile lookup; private accounts return 401; counter fields omitted for unauthenticated callers.
- `GET /api/v1/users/me/settings` — returns the authenticated user's notification and privacy settings.
- `PATCH /api/v1/users/me/settings` — partial settings update with patch semantics.
- RabbitMQ topology now declares the `social.events` topic exchange, `social.events.dlx`, `mail.queue`, and `mail.dlq` for mail side-effect events only.
- RabbitMQ environment variables are documented in the environment template with publisher confirms and returns enabled.
- Transactional outbox storage now records versioned domain event envelopes in PostgreSQL before RabbitMQ publishing.
- Scheduled outbox publisher now publishes due events to RabbitMQ with correlated publisher confirms, bounded retry metadata, and dead-letter terminal state.
- Consumer inbox storage now records processed message IDs for idempotent RabbitMQ consumers.
- Auth registration, verification resend, password reset, password changed, and OAuth-only reset flows now record mail side-effect events through the transactional outbox instead of sending mail directly.
- Forgot-password handling now records durable outbox events inside a short transaction and applies a configurable response-time floor after the transaction to reduce account-enumeration timing signals.
- Auth mail RabbitMQ consumer now processes `mail.queue` events with manual ack, idempotent inbox deduplication, bounded retry, and DLQ routing.
- Synchronous Resend mail sender added for RabbitMQ consumers while keeping the existing async mail facade for non-consumer callers.
- Social follow endpoint now creates accepted follows for public accounts, pending follow requests for private accounts, and records follow events through the transactional outbox.
- Media upload-complete now persists validated media metadata, derives CDN URLs server-side, and records `media.uploaded.v1` outbox events after successful inserts.
- Media upload URL endpoint now returns short-lived Cloudflare R2 pre-signed PUT URLs with backend-generated storage keys.

### Fixed
- `UserMapper.toProfileResponse` and `toPublicProfileResponse` now correctly map `isPrivate` and `isVerified` from the `User` entity; previously, MapStruct's JavaBeans convention stripped the `is` prefix from the boolean getter names (`isPrivate()` → property `private`, `isVerified()` → property `verified`), which did not match the record constructor parameter names (`isPrivate`, `isVerified`), causing both fields to silently default to `false` in every response.
- `UserServiceImpl.getUserProfile` no longer rejects authenticated callers viewing a private account; the visibility guard now reads `if (user.isPrivate() && !isAuthenticated)` so only unauthenticated requests receive HTTP 401 for private profiles.
- `UserStateValidator.enforceEmailVerified` now throws `AppException(AUTH_EMAIL_NOT_VERIFIED)` instead of `AUTH_ACCOUNT_INACTIVE`, giving callers a dedicated, distinguishable error code for the unverified-email case.
- `AuthServiceImpl.resetPassword` catch clause extended to `TokenNotFoundException | TokenExpiredException` so an expired password-reset token is mapped to `AUTH_RESET_TOKEN_INVALID` (HTTP 400) rather than propagating as an unhandled exception, mirroring the `verifyEmail` flow.
- Default async executor selection is explicit when scheduled outbox publishing is enabled.
- RabbitMQ template mandatory publishing is enabled so unroutable outbox messages can be detected by publisher returns.
- Outbox publisher now uses short transactional claim leases and per-event state commits instead of holding one batch transaction across RabbitMQ publisher confirms.
- Media upload-complete now only maps PostgreSQL unique violations to duplicate storage-key conflicts instead of masking unrelated database integrity failures.

### Changed
- `User` and `UserSettings` entities, enums, converters, and repositories relocated from `com.app.modules.auth` to `com.app.modules.users`; all auth module import references updated accordingly.
- Auth mail side-effect documentation now points to outbox events and the mail consumer token-generation flow; raw verification/reset tokens are no longer created in the auth request path.
- Auth mail events now use `actorId = null` for unauthenticated verification resend and forgot-password requests while keeping `aggregateId` as the target user id.
- Auth mail RabbitMQ bindings now live with the auth module event contracts instead of the shared RabbitMQ infrastructure config.
- Media API now exposes OpenAPI documentation for direct-upload URL issuance and upload confirmation.

### Tests
- Added RabbitMQ topology tests covering active mail queues, mail event bindings, dead-letter binding, and inactive future queues.
- Added outbox enqueue tests covering event metadata, JSON payload envelope, required field validation, and absence of direct RabbitMQ publishing.
- Added outbox publisher tests covering confirm success, publish failure retry scheduling, max-attempt dead state, nack handling, timeout handling, and database publisher updates.
- Added RabbitMQ Testcontainer coverage for outbox publisher delivery and unroutable-message retry behavior.
- Added outbox claim-lease tests covering expired `PROCESSING` event reclaim and stale-claim result guards.
- Added processed-message inbox tests covering first processing, duplicate skipping, and rollback on failed processing.
- Added auth mail-event tests covering minimal outbox payloads and the absence of raw verification/reset token creation in auth mail request paths.
- Added forgot-password event-routing and timing-equalizer tests, plus controller integration coverage that register writes auth mail outbox events without token-bearing payloads.
- Added auth mail consumer unit and RabbitMQ Testcontainer coverage for successful delivery, duplicate skipping, transient retry, invalid payload DLQ routing, and DLQ publish failure requeue behavior.
- Added social follow service, outbox event, repository, and trigger-counter coverage.
- Added media upload-complete tests covering metadata validation, synchronous media persistence, outbox event payloads, and database-generated media IDs.
- Added media upload URL validation, storage key generation, and R2 presigner configuration tests.
- Added media upload-complete tests covering current-user storage-key ownership and non-unique database integrity failures.

### CI
- Replaced split SonarCloud Maven steps with a single `verify sonar-maven-plugin:sonar` invocation; added SonarCloud package cache and `GITHUB_TOKEN` env declaration.

### Security
- OAuth2 authorization-request cookie is now signed with HMAC-SHA256 using a secret configured via `app.security.cookie-signing-secret` (`APP_COOKIE_SIGNING_SECRET`); cookies with an absent or invalid signature are silently rejected before deserialization, preventing `state`-parameter forgery (AUTH-027).
- Replaced blanket `csrf.disable()` with `csrf.ignoringRequestMatchers("/api/**")` so CSRF protection remains active on the OAuth2 callback paths (`/login/oauth2/code/**`); Spring Security now verifies the `state` parameter on every callback (AUTH-027).
- Introduced `CookieOAuth2AuthorizationRequestRepository` to store the pending `OAuth2AuthorizationRequest` in an `HttpOnly`, `SameSite=Lax`, `Path=/`, 5-minute cookie instead of the HTTP session, preserving `SessionCreationPolicy.STATELESS` while keeping `state`-parameter CSRF protection intact; the `Secure` flag is enabled only under the `prod` Spring profile (AUTH-027).
- Enforced strict JWT issuer validation (`iss` claim now rejected when absent); added mandatory `audience` (`aud`) claim issuance and validation (AUTH-001, AUTH-024).
- Replaced blanket `/api/v1/auth/**` `permitAll` with explicit per-endpoint security rules; `POST /api/v1/auth/logout` and `POST /api/v1/auth/change-password` now require authentication (AUTH-002).
- `JwtAuthenticationFilter` now rejects non-ACTIVE users (BANNED/SUSPENDED/DEACTIVATED) on every request rather than waiting for access-token expiry (AUTH-003).
- Introduced `IpExtractor` with trusted-proxy whitelist (`app.security.trusted-proxy-cidrs`); `X-Forwarded-For` is honored only for proxies that match the whitelist (AUTH-004).
- Dropped unused plaintext-credential columns (`access_token`, `refresh_token`, `token_expires_at`) from `oauth_accounts` via V19 migration (AUTH-005).
- `forgotPassword` keeps a generic response across unknown/inactive/active code paths; OAuth-only accounts (no local password) receive an informational mail event instead of a reset token (AUTH-006, AUTH-026).
- Email-verification token failures now return `AUTH_VERIFY_TOKEN_INVALID` (400) without leaking internal exception messages; `GlobalExceptionHandler` no longer echoes `TokenNotFoundException`/`TokenExpiredException` messages to clients (AUTH-007).
- `RateLimiterServiceImpl` now fails closed on Redis failures, denying requests rather than allowing brute-force traffic through an outage (AUTH-008).
- `AuthRateLimitFilter` now covers all sensitive auth endpoints (register, login, refresh, forgot-password, reset-password, verify-email, resend-verify); per-method `@RateLimiter` annotations removed from `AuthController` in favor of the single Redis-backed filter (AUTH-009).
- One-time token creation (email verification, password reset) is now atomic via a Lua script; prior get-then-delete-then-set sequence was non-atomic (AUTH-011).
- `CustomOidcUserService` no longer hardcodes `OAuthProvider.GOOGLE`; provider is resolved from the `OidcUserRequest` registration ID with explicit rejection for unwired providers (AUTH-013).
- `OAuth2AuthenticationFailureHandler` no longer echoes the raw exception message to the response body; provider error details are logged server-side at WARN (AUTH-014).
- `CachedBodyHttpServletRequest` now enforces `app.security.max-login-body-bytes` (default 2048); request bodies exceeding the limit return 400 instead of consuming unbounded memory (AUTH-015).
- `RefreshTokenServiceImpl.rotate()` now performs equivalent DB work for unknown vs known-revoked tokens, masking the timing side-channel that previously distinguished these states (AUTH-016).
- One-time tokens and refresh tokens are now generated from `SecureRandom` (32 bytes / 43-char URL-safe Base64) instead of `UUID.randomUUID()` (AUTH-017).
- `TokenBlacklistServiceImpl.blacklist()` now fails explicitly on Redis failure (throws `AppException(INTERNAL_ERROR)`); logout no longer silently leaves an unrevoked access token (AUTH-018).
- All 429 responses now include a `Retry-After` header (filter path uses the matched rule window; `BaseController` fallback uses 30s) (AUTH-019).
- CORS configuration expanded: `Accept`, `Accept-Language`, `X-Requested-With`, `X-Device-ID` are now allowed; `Retry-After` and `X-Total-Count` are exposed (AUTH-020).
- JWT access tokens now include a `nbf` (not-before) claim equal to `iat`, validated by `JwtTimestampValidator` (AUTH-021).
- Strict request body deserialization enabled globally (`spring.jackson.deserialization.fail-on-unknown-properties=true`) plus `@JsonIgnoreProperties(ignoreUnknown = false)` on `RegisterRequest` and `ResetPasswordRequest` (AUTH-022).
- Production profile sets `logging.file.path: /var/log/app` so the `${user.home}` fallback applies only to dev/test (AUTH-023).
- Media upload-complete now rejects client-submitted storage keys outside the authenticated user's generated upload prefix.

### Added
- SonarCloud static analysis integrated into CI: `sonarcloud.yml` workflow runs on every push to `main` and on every pull request targeting `main`; JaCoCo coverage report at `target/site/jacoco/jacoco.xml` is forwarded to SonarCloud for coverage metrics. **Action required:** disable "Automatic Analysis" in SonarCloud project settings (Administration → Analysis Method) to prevent conflicts with this CI-based analysis.
- CI workflow `ci-test.yml` runs the Maven test suite on pull requests targeting `main` or `develop`; job is reporting-only and does not block merges.
- CI secrets audit report saved to `.claude/workspace/ci-secrets-audit.md`; confirmed no GitHub Actions secrets are required — all runtime values are provided by `@ServiceConnection`, `@DynamicPropertySource`, or `@TestPropertySource` in the test classes.
- `OpenApiConfig` bean updated to source the server URL from `AppProperties.baseUrl()` and produce API title `"App API"`, version `"1.0.0"`, and a global `bearerAuth` Bearer JWT security scheme.
- `AuthApi` interface (`modules/auth/api`) carrying all `@Tag`, `@Operation`, `@ApiResponses`, and `@Parameter` OpenAPI annotations for the 8 auth endpoints; `AuthController` implements this interface and contains zero documentation annotations.
- `docs/modules/OPENAPI_GUIDE.md`: developer guide explaining how to document a new module using the Interface Segregation pattern, including step-by-step instructions, rules, a reference endpoint table, and common mistakes.

### Changed
- `AuthController` refactored to implement `AuthApi`; class-level `@Tag` and `@RequestMapping` removed (now on the interface); all `@Operation`, `@ApiResponses`, and `@Parameter` annotations removed from handler methods.
- `GIT_WORKFLOW.md` agent rule documenting branch naming, Conventional Commits format, allowed scopes, PR size labels, and discrepancies found between `CONTRIBUTING.md` and the actual pr-lint/pr-size workflow enforcement.
- 11 agent skills under `.claude/skills/`: `skill-jpa-entity`, `skill-spring-repository`, `skill-spring-service`, `skill-rest-controller`, `skill-mapstruct-mapper`, `skill-dto`, `skill-flyway-migration`, `skill-exception-handling`, `skill-redis-key`, `skill-test-unit`, `skill-test-integration` — each derived from the implemented `auth` and `mail` modules.
- 4 agent workflows under `.claude/workflows/`: `workflow-implement-module`, `workflow-add-flyway-migration`, `workflow-add-api-endpoint`, `workflow-code-review`.
- `DOC_FIRST.md` agent rule mandating that `GLOBAL_RULES.md` and the relevant module `DATA_RULES.md` (plus `STRUCT.md` for new module implementations) are read before any feature implementation or business-logic change.

### Changed
- `base.md`: added missing `description` field to frontmatter.
- `changelog_rule.md`: corrected description (was a copy of struct.md's description); changed trigger from `model_decision` to `always_on` to match the rule's stated requirement.
- `doc_first.md`: corrected description (was a copy of struct.md's description).

### Fixed
- `refresh` flow transaction boundary corrected: `rotate()` and `revoke()` in `RefreshTokenServiceImpl` now use `REQUIRES_NEW` propagation so both the old-token revocation and the new-token revocation commit to the database independently, even when the outer request transaction rolls back after a banned or suspended account check. Previously, the entire transaction rolled back and the original refresh token remained active.

### Fixed
- Banned, suspended, and deactivated users can no longer receive or redeem a password-reset link; `forgotPassword` returns silently and `resetPassword` throws the appropriate locked/inactive error.
- Password-reset email link now uses `mail.frontend-base-url` and the configurable `mail.reset-password-path` property (default `/reset-password`) instead of the backend API URL, making the link functional in a browser.
- `TokenNotFoundException` thrown during password-reset token consumption is now converted to `AppException(AUTH_RESET_TOKEN_INVALID)` at the service layer before reaching `GlobalExceptionHandler`, returning HTTP 400 with the correct error code instead of HTTP 404.

### Added
- `MailProperties.resetPasswordPath` field (default `/reset-password`) bound to `app.mail.reset-password-path` / `MAIL_RESET_PASSWORD_PATH` environment variable, making the frontend reset-form path configurable.

### Added
- Implementation plan for OpenAPI interface segregation pattern in the `auth` module at `docs/plans/openapi-interface-segregation-plan.md`.


- Integrated `springdoc-openapi-starter-webmvc-ui` 3.0.3 for interactive API documentation.
- `OpenApiConfig` bean exposing title, version, server entry from `app.base-url`, and global Bearer JWT security scheme.
- OpenAPI JSON endpoint at `/api-docs` and Swagger UI at `/swagger-ui` (dev profile only; disabled in prod).
- Full `@Tag`, `@Operation`, `@ApiResponses`, and `@SecurityRequirement` annotations on all `AuthController` endpoints.
- `@Schema` annotations with descriptions, examples, and required-mode markers on all `auth` request and response DTOs.
- Swagger UI paths (`/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html`) added to `SecurityConfig` permit list.


- `report_reason_configs` table appended to V18 Flyway migration: stores display metadata and per-`report_type` scope control for all 8 `report_reason` enum values, seeded with one row per reason.
- `GLOBAL_RULES.md`: new `Enum vs. Config Table Relationship` section documenting the contract between PostgreSQL enum columns and config tables (`notification_type_configs`, `moderation_action_configs`, `report_reason_configs`).
- `media/DATA_RULES.md`: server-side metadata validation rule for `media_assets` insert — specifies that the server must validate all client-submitted metadata before creating the record, and that validation failure must leave no orphaned row.

### Security
- LOW: documented a TOCTOU race in `TokenServiceImpl.createToken` (4-step issue flow is non-atomic so concurrent same-user issuance can briefly leak orphan single-use tokens until natural TTL); deferred per Phase 3 unit-test contract that asserts the current non-atomic shape.
- MEDIUM: aligned `TokenNotFoundException` / `TokenExpiredException` HTTP mapping with the audit-mandated contract — verify-email with consumed/expired/unknown tokens now returns HTTP 404 (`NOT_FOUND`) instead of HTTP 400 (`AUTH_RESET_TOKEN_INVALID`), so the three states are indistinguishable to the caller.

### Fixed
- `GlobalExceptionHandler.handleInvalidToken` now maps token-not-found and token-expired exceptions to `ApiErrorCode.NOT_FOUND` (HTTP 404).
- `ApplicationTests.contextLoads` updated to use Testcontainers for PostgreSQL and Redis; Spring Boot 4 autoconfigure exclude paths corrected (`org.springframework.boot.{jdbc,hibernate,data.jpa,flyway,data.redis,amqp}.autoconfigure.*`).
- `AuthControllerIT` annotated with `@AutoConfigureTestRestTemplate` so the `TestRestTemplate` bean is registered under Spring Boot 4 (which moved this autoconfig out of the default `@SpringBootTest` activation set).

### Changed
- Bumped Testcontainers from 1.21.0 to 1.21.4 to ship a docker-java client compatible with Docker Engine ≥ 25 (which requires Docker API ≥ 1.40).
- Maven Surefire now includes `**/*IT.java` in the test phase so the auth integration test runs as part of `./mvnw test`.

### Tests
- Rewrote `TokenServiceImplTest` (Redis-backed): added `consumeEmailVerificationToken_valid_executesLuaScript` to assert the atomic-consume Lua script execution.
- Added `TokenBlacklistServiceImplTest` covering positive / zero / negative TTL stores, key-exists / key-absent / null-Redis-result reads, and null/blank `jti` defensive paths.
- Added `RateLimiterServiceImplTest` covering under-limit, at-limit, over-limit, and null-script-result outcomes.
- Extended `AuthServiceImplTest`: `logout_blacklistsAccessTokenAndRevokesRefreshToken`, `logout_invalidAccessToken_stillRevokesRefreshToken`, `logout_noAuthContext_blacklistsNothingAndRevokesRefreshToken`.
- Extended `JwtTokenProviderTest`: `generateAccessToken_containsJtiClaim`, `generateAccessToken_eachInvocationProducesUniqueJti`, `validateAndParse_returnsJtiInClaims`, `validateAndParse_returnsExpiresAtInClaims`.
- Extended `GlobalExceptionHandlerTest`: `tokenNotFoundException_returnsNotFound`, `tokenExpiredException_returnsNotFound`.
- Extended `AuthControllerIT`: `verifyEmail_invalidToken_returnsNotFound`, `verifyEmail_consumedToken_returns404`, `verifyEmail_unknownToken_returnsSameNotFoundAsConsumed`, `login_logout_reuseAccessToken_returns401` (blacklist), `login_wrongPassword_5timesSameIp_6thReturns429`, `forgotPassword_3timesSameIp_4thReturns429`.
- Test totals: 115 tests run, 0 failures, 0 errors.

### Changed
- Email-verification and password-reset tokens now live in Redis (24h and 15m TTL respectively), keyed by `auth:token:email-verification:{sha256}` and `auth:token:password-reset:{sha256}`; consumption is atomic via Lua and a reverse `…:user:{userId}` index ensures issuing a new token invalidates the prior pending one.
- `TokenService.consumeEmailVerificationToken` / `consumePasswordResetToken` now return the owning `UUID` so callers no longer need a separate hash lookup.

### Removed
- PostgreSQL tables `email_verification_tokens` and `password_reset_tokens` (dropped from `V02__create_users_auth_tables.sql`); their JPA entities and repositories.
- `TokenAlreadyUsedException` — Redis cannot distinguish "expired" from "already used"; both now surface as `TokenNotFoundException` mapped to `AUTH_RESET_TOKEN_INVALID`.

### Added
- Redis-backed JWT access-token blacklist: logout now records the token's `jti` for its remaining lifetime so it cannot authenticate again before its natural expiry.
- Redis-backed sliding-window rate limiter on `POST /api/v1/auth/login` (5/15min, IP+email keyed), `/forgot-password` and `/verify-email/resend` (3/hr, IP keyed), executed via an atomic `INCR`+`EXPIRE` Lua script.
- `app.rate-limit.*` configuration namespace bound to `RateLimitProperties` for per-endpoint limit and window tuning.
- MapStruct 1.6.3 dependency and annotation processor; `lombok-mapstruct-binding` 0.2.0 wires Lombok before MapStruct in both compile and test-compile phases
- `AuthMapper` interface in `modules/auth/mapper/` maps `User` + `emailVerified` boolean to `UserSummaryResponse` via MapStruct Spring component model
- `SecurityMapper` interface in `common/security/` maps `User` entity to `UserPrincipal` security principal via MapStruct Spring component model

### Changed
- Moved `UserRole`, `UserStatus`, and `OAuthProvider` enums from `modules/auth/entity/` to new `modules/auth/enums/` package; all import sites updated
- `AuthServiceImpl` and `OAuth2AuthenticationSuccessHandler` now delegate `User` → `UserSummaryResponse` mapping to the injected `AuthMapper` bean, replacing inline field-by-field construction
- `JwtAuthenticationFilter` delegates `User` → `UserPrincipal` construction to the injected `SecurityMapper` bean

### Fixed
- Resolved `ObjectMapper` bean injection failure in `SecurityConfig`, `OAuth2AuthenticationSuccessHandler`, and `OAuth2AuthenticationFailureHandler` by migrating imports from `com.fasterxml.jackson.databind.ObjectMapper` (Jackson 2.x) to `tools.jackson.databind.ObjectMapper` (Jackson 3.x, required by Spring Boot 4)
- Removed `ObjectMapper` from `SecurityConfig` constructor; injected via `@Autowired` field to break the circular dependency that prevented context startup
- Fixed `ApplicationTests.contextLoads` failure by adding `src/test/resources/application-test.yml` with placeholder env-var values and excluding infrastructure auto-configurations (DataSource, JPA, Flyway, Redis, RabbitMQ) that require live services

### Security
- Hardened JWT decoder: `JwtTokenProvider` now installs a `DelegatingOAuth2TokenValidator` combining `JwtTimestampValidator` with `JwtIssuerValidator(properties.issuer())`, so signature alone is no longer sufficient — the `iss` claim is enforced on decode.
- Closed login timing oracle: `AuthServiceImpl.login` now invokes BCrypt against a precomputed dummy hash on the unknown-email, missing-credential, and null-password-hash branches, eliminating email enumeration via response latency.
- OAuth2 link-by-email gate: `CustomOidcUserService` refuses to attach an OAuth identity to a pre-existing local account unless the IdP confirms `email_verified=true`, preventing OAuth-based account takeover via misconfigured IdPs.
- Refresh-token replay detection: `RefreshTokenRepository.revokeByTokenHash` returns the affected row count; `RefreshTokenServiceImpl.rotate` treats a zero-row update as a concurrent-rotation race and revokes every active session for the user (token theft per OAuth 2.0 BCP).
- Removed CORS wildcard fallback: `SecurityConfig.corsConfigurationSource` no longer falls back to `setAllowedOriginPatterns("*")` when `app.cors.allowed-origins` is empty; an unset origin list is now a deny-all CORS policy, refusing any wildcard combined with `allowCredentials=true`.
- Locked actuator endpoints: only `/actuator/health` is permitted without auth; `/actuator/**` now requires `ROLE_ADMIN`, blocking ordinary authenticated users from reading `/actuator/prometheus`, `/actuator/env`, etc.
- Stopped persisting Google OAuth access token: `OAuthAccount.accessToken` is no longer stored at link time, removing an unused secret from the database-compromise blast radius.

### Fixed
- `CustomOidcUserService.resolveUniqueUsername` random-suffix branch now re-checks uniqueness via `ThreadLocalRandom` and a bounded retry loop, preventing the rare unique-constraint violation that previously surfaced as a 500.

### Tests
- Unit tests added: `JwtTokenProviderTest` (HS256 happy path, expired-token rejection, tampered-signature rejection, wrong-algorithm rejection, wrong-issuer rejection, non-UUID subject rejection), `RefreshTokenServiceImplTest` (issue/hash, TTL, rotate happy path, expired/revoked/unknown rejection, concurrent-rotation theft detection, revoke idempotency, bulk revoke), `AuthServiceImplTest` (register conflicts, register success + mail dispatch, login timing-safe failure shapes, status branches, refresh, logout idempotency, forgot/reset flows).
- Integration test added: `AuthControllerIT` boots the full Spring Boot context against a Testcontainers PostgreSQL container, runs Flyway migrations, and exercises 16 HTTP scenarios covering register, login, refresh rotation/replay, logout, JWT-protected endpoint validation, and email-verification / forgot-password contracts.

### Added
- Google OAuth2 / OIDC sign-in: `OAuthAccount` entity with case-mapping converter, `OAuthAccountRepository`, `CustomOidcUserService` resolving a Google identity to a local user (link by provider id, fall back to email match, otherwise auto-register with `email_verified=true` and no password), `CustomOidcUser` decorator, and JSON `OAuth2AuthenticationSuccessHandler` / `OAuth2AuthenticationFailureHandler` returning the standard `ApiResponse` envelope
- `spring.security.oauth2.client.registration.google.*` configuration block driven by `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`, and `APP_BASE_URL` (redirect URI is environment-dependent)
- `SecurityConfig` now installs `oauth2Login(...)` on the existing single `SecurityFilterChain`, mounting authorize/callback under `/api/v1/auth/oauth2/{authorize,callback/*}` with the custom OIDC user service and JSON handlers
- Stateless JWT authentication module under `common/security/` — `JwtProperties`, `JwtTokenProvider` backed by Spring Security's `NimbusJwtEncoder`/`Decoder`, `JwtAuthenticationFilter`, `UserPrincipal`, `SecurityUtils`, `CorsProperties`, and a single-chain `SecurityConfig` enforcing stateless session policy with BCrypt strength 12
- `RefreshTokenService` interface and `RefreshTokenServiceImpl` issuing UUID-based opaque refresh tokens persisted as SHA-256 hashes; supports rotation, single-token revoke, and bulk revoke per user
- `User`, `UserCredential`, `UserSettings`, and `RefreshToken` JPA entities with `UserRole`/`UserStatus` enums and case-mapping `AttributeConverter`s for the lowercase Postgres custom enum types
- `UserRepository`, `UserCredentialRepository`, `UserSettingsRepository`, and `RefreshTokenRepository` Spring Data JPA repositories
- `AuthService` and `AuthServiceImpl` covering register, login, refresh, logout, email verification, resend verification, forgot password, and reset password flows; verification and password change emails dispatched via the existing `MailService`
- `AuthController` exposing `/api/v1/auth/{register,login,refresh,logout,verify-email,verify-email/resend,forgot-password,reset-password}` returning `ApiResponse` envelopes
- New `ApiErrorCode` entries: `USER_EMAIL_ALREADY_EXISTS`, `USER_USERNAME_ALREADY_EXISTS`, `AUTH_RESET_TOKEN_EXPIRED`, `AUTH_RESET_TOKEN_USED`
- `GlobalExceptionHandler` now translates `TokenNotFoundException`, `TokenExpiredException`, and `TokenAlreadyUsedException` into typed 4xx `ApiResponse` failures
- `RedisConfig` in `common/config/` registers a `LettuceClientOptionsBuilderCustomizer` bean that forces RESP2 protocol, resolving `NOAUTH` errors caused by Lettuce 6 sending `HELLO 3` before authentication against a `requirepass`-only Redis server

### Changed
- `app.jwt.*` is the canonical JWT configuration namespace (`secret`, `issuer`, `access-token-ttl`, `refresh-token-ttl`); the obsolete `security.jwt.*` block has been removed from `application-dev.yml` and `application-prod.yml`
- `spring.datasource.hikari.data-source-properties.stringtype=unspecified` enabled to allow VARCHAR binding to Postgres custom enum and `inet` columns
- `Application.java` now uses `@ConfigurationPropertiesScan` so `JwtProperties`, `CorsProperties`, and `AppProperties` bind without per-config registration
- `pom.xml` adds `spring-boot-starter-oauth2-client` and `spring-security-oauth2-jose` to provide `NimbusJwtEncoder`/`NimbusJwtDecoder`

### Changed
- `CLAUDE.md` reduced to a minimal pointer file; all project rules, architecture state, and conventions moved to `.claude/rules/AGENT.md`
- `.claude/rules/AGENT.md` rewritten with full codebase audit results: accurate module status table, complete `common/` class inventory, Flyway migration status (V01–V17), mail module documentation, API response contract, known gaps, and updated environment variable table including `RESEND_API_KEY`

### Added
- `MailProperties` standard class (`@Component` + `@ConfigurationProperties`) at `common/mail/config/` with `fromAddress`, `fromName`, `appName`, and `frontendBaseUrl` fields
- `MailTemplate` enum at `common/mail/enums/` consolidating all template paths and default subjects
- `MailRequest` DTO at `common/mail/dto/` for carrying recipient and template variable data
- `MailTemplateRenderer` component at `common/mail/util/` isolating Thymeleaf rendering from dispatch logic
- `MailService` interface and `MailServiceImpl` at `common/mail/service/` replacing the former `modules/mail/` placement; provider errors now surface as `AppException` with `SERVICE_UNAVAILABLE`
- `app.mail.from-name`, `app.mail.app-name`, and `app.mail.frontend-base-url` properties bound via the new `MailProperties`; `application-prod.yml` reads these from environment variables
- Unit tests for `MailTemplateRenderer` and `MailServiceImpl` covering variable assembly, template dispatch, and exception wrapping

### Changed
- `MailServiceImpl` variable keys renamed from `recipientName` to `toName`; `appName` added as a template variable sourced from `MailProperties`
- Email templates updated to use `toName` and `appName` Thymeleaf variables; hardcoded branding removed
- `MailConfig` removed `MailProperties` from `@EnableConfigurationProperties` since the class is now a `@Component` self-registering bean

### Removed
- `MailProperties` record from `common/config/` replaced by the standard class at `common/mail/config/`
- `MailService`, `MailServiceImpl`, and `MailSendException` from `modules/mail/` relocated into `common/mail/`

### Added
- `ApiErrorCode` enum in `common/response/` with typed HTTP status, machine-readable code, and default message for Common and Auth error groups
- `ApiSuccessCode` enum in `common/response/` with OK, CREATED, ACCEPTED, NO_CONTENT constants
- `AppException` in `common/exception/` backed by `ApiErrorCode`, replacing raw integer status codes with typed error codes at the service layer
- `ApiResponse.success()` and `ApiResponse.failure()` factory overloads accepting `ApiSuccessCode`/`ApiErrorCode` enums with optional custom message and data payload
- `BaseController` resilience fallback methods for rate limiter and circuit breaker using `ApiErrorCode`-backed responses

### Changed
- `GlobalExceptionHandler` rewritten to use `AppException` + `ApiErrorCode` for all handler return paths; validation handlers now collect all field/constraint violations into a `Map<String, String>` returned as `data`
- `MailServiceImpl` expiry constants promoted to `public` for test visibility

### Added
- Generic `ApiException(int statusCode, String errorCode, String message)` in `common/exception/` to decouple service-layer errors from any shared enum catalogue
- Uniform `ApiResponse<T>` envelope under `common/response/` with `ok`, `created`, and `error` static factories and an embedded `Instant` timestamp
- `PageResponse<T>` (offset-based, built from Spring Data `Page`) and `CursorPageResponse<T>` (Relay-spec cursor pagination with embedded `PageInfo`) under `common/response/`
- `ApiConstants` central registry of `/api/v1` route constants for auth, users, posts, comments, stories, social, messages, notifications, hashtags, media, reports, and admin endpoints
- `spring-boot-starter-validation` dependency for `jakarta.validation` support required by the new framework-level exception handler
- Transactional mail subsystem under `common/mail` backed by the Resend Java SDK with a dedicated `mailTaskExecutor` async pool, exposing email verification, password reset, welcome, and password-changed messages
- Thymeleaf email templates under `templates/mail/` for verification, password reset, welcome, and password-changed notifications
- `app.mail.from-address`, `app.mail.from-name`, and `app.base-url` configuration bound via `MailProperties` and `AppProperties`, all sourced from environment variables
- Single-use, SHA-256-hashed token issuance and consumption in the `auth` module: `EmailVerificationToken` (24h TTL) and `PasswordResetToken` (15min TTL) entities, JPA repositories, and `TokenService` contract
- `.env.example` template enumerating every environment variable consumed by the application (database, Redis, JWT, app URL, CORS, Resend mail credentials)
- Unit test coverage for `TokenServiceImpl`, `MailServiceImpl`, and `GlobalExceptionHandler`
- `CHANGELOG_RULE.md` in `.claude/rules/` defining mandatory post-task changelog requirements following Keep a Changelog 1.1.0
- Post-Task Requirements section in `.claude/rules/AGENT.md` enforcing the changelog obligation after every completed task
- `CHANGELOG_RULE.md` reference in `CLAUDE.md` pre-read list and workflow pipeline comment

### Changed
- `GlobalExceptionHandler` rewritten to handle only generic, framework-level exceptions (`ApiException`, validation, malformed request, type mismatch, not found, method not allowed, data integrity, optimistic lock, access denied, authentication, upload size, illegal argument/state, entity not found, transaction system, and a catch-all 500), all responding with `ApiResponse<Void>`
- Token exceptions (`TokenNotFoundException`, `TokenExpiredException`, `TokenAlreadyUsedException`) relocated from `common/exception/` to `modules/auth/exception/` to enforce module ownership of domain errors
- `MailSendException` relocated from `common/exception/` to `common/mail/` alongside the mail service it belongs to
- `GlobalExceptionHandlerTest` rewritten against the new envelope and handler surface
- `CLAUDE.md` rewritten to accurately describe this project (Spring Boot 4.0.6 social network) — replaced all content copied from an unrelated recruitment ATS project
- `.claude/rules/STRUCT.md` rewritten to reflect the actual codebase: correct technology stack, module roster, database schema, infrastructure services, and domain-specific notes

### Removed
- `ErrorResponse` record and the legacy `common/exception/` token and mail exception classes superseded by `ApiException` and the relocated domain exceptions
