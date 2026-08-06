# Message Module — Data Rules

**Implementation status**: Conversation and participant management is implemented.
Direct and group conversation creation, cursor-paginated conversation listing with unread counts, participant listing, adding and removing participants, group admin promotion on departure, leaving a conversation, and group metadata updates are all in place.
There is no send-message or list-messages endpoint.
The `messages` table exists and is read for unread counts, but no API path ever writes to it, so the message-level rules in Section 3B below are unexercised until that endpoint ships.

---

## Section 1: Canonical Data

| Table | Key Columns | Notes |
|-------|-------------|-------|
| `conversations` | `id`, `is_group`, `group_name`, `group_avatar_url`, `created_by` | A conversation thread, either 1-1 or group. `created_by` is SET NULL if the creator deletes their account. |
| `conversation_participants` | `conversation_id`, `user_id`, `is_admin`, `joined_at`, `left_at`, `last_read_at` | Membership record for each participant. `left_at IS NOT NULL` means the user has left the conversation. |
| `messages` | `id`, `conversation_id`, `sender_id`, `message_type`, `content`, `media_asset_id`, `shared_post_id`, `shared_story_id`, `reply_to_id`, `is_deleted`, `deleted_at` | Individual messages. Soft-deleted via `is_deleted = TRUE` + `deleted_at`. |

These tables cannot be rebuilt from any other source if lost.

---

## Section 2: Derived Data / Cache / Projection

| Data | Location | Rebuilt From | Rebuild Trigger |
|------|----------|--------------|-----------------|
| `conversations.last_message_at` | `conversations` table | Latest `messages.created_at` for the conversation | Trigger `trg_conversation_last_message` (V16) |
| `conversations.updated_at` | `conversations` table | Auto-maintained | Trigger `trg_conversations_updated_at` (V16) |
| Unread message count | Computed at query time | `COUNT(*)` from `messages` where `created_at > conversation_participants.last_read_at` and `is_deleted = FALSE` | Query-time |
| Conversation list cache | Redis | Rebuild from `conversations` joined with `conversation_participants` | Cache miss or TTL expiry |

---

## Section 3: Business Rules

### A. Rules Enforced by the Database

| Rule | Enforced By |
|------|-------------|
| `messages.message_type` must be one of `'text'`, `'image'`, `'video'`, `'post_share'`, `'story_share'` | `message_type` enum |
| `conversation_participants` allows at most one row per (conversation, user) pair | Compound `PRIMARY KEY (conversation_id, user_id)` |
| `messages.shared_post_id` becomes NULL if the shared post is deleted | `ON DELETE SET NULL` |
| `messages.shared_story_id` becomes NULL if the shared story is deleted | `ON DELETE SET NULL` |
| `messages.reply_to_id` becomes NULL if the replied-to message is deleted | `ON DELETE SET NULL` |
| `conversations.created_by` becomes NULL if the creator deletes their account | `ON DELETE SET NULL` |
| Deleting a conversation cascades to participants and messages | `ON DELETE CASCADE` |
| Deleting a user cascades to their `conversation_participants` rows and their sent messages | `ON DELETE CASCADE` on FK to `users` |

### B. Rules Enforced by Application Code

| Rule | Service / Component |
|------|---------------------|
| A user may only send messages to conversations they are an active participant of (`left_at IS NULL`) | `[NOT YET IMPLEMENTED]` — blocked on a send-message endpoint |
| For a 1-1 conversation (`is_group = FALSE`), there must be exactly 2 participants | `ConversationServiceImpl.createDirectConversation`, enforced by construction: it is the only path that creates a non-group conversation, and it always inserts the actor plus exactly one target |
| A blocked user may not initiate or reply to messages with the blocker | Initiation enforced by `ConversationServiceImpl.assertNotBlocked`, called from `createDirectConversation`, `createGroupConversation`, and `addParticipants`; the "reply" half is `[NOT YET IMPLEMENTED]` — blocked on a send-message endpoint |
| Message soft-delete sets `is_deleted = TRUE` and `deleted_at = NOW()`; message `content` should be cleared or replaced with a tombstone | `[NOT YET IMPLEMENTED]` — no code path writes to `messages` at all |
| Only the message sender may delete their own message | `[NOT YET IMPLEMENTED]` — blocked on a send-message endpoint |
| `last_read_at` on `conversation_participants` is updated when the user reads the conversation | `[NOT YET IMPLEMENTED]`; `last_read_at` is only ever read, by `MessageRepository.countUnreadPerConversation` |
| A message of type `'post_share'` must have `shared_post_id` set; `'story_share'` must have `shared_story_id` set | `[NOT YET IMPLEMENTED]` — no code path writes to `messages` at all |
| A message of type `'image'` or `'video'` must have `media_asset_id` set | `[NOT YET IMPLEMENTED]` — no code path writes to `messages` at all |
| Group admins may add/remove participants and update `group_name` / `group_avatar_url` | `ConversationServiceImpl.addParticipants` / `removeParticipant` / `updateGroup`, all gated by `requireGroupAdmin` |
| Sending a message generates a `message` notification for all participants except the sender | `[NOT YET IMPLEMENTED]` — blocked on a send-message endpoint; see the `notification` module's dead `MESSAGE` toggle |
| `user_settings.allow_message_requests` governs whether non-followers can initiate a conversation | `ConversationServiceImpl.assertMessageRequestAllowed` |

**`sender_id` cascade behavior** `[KNOWN GAP — pending migration fix]`:
- The current schema defines `sender_id` with `ON DELETE CASCADE`, meaning deleting a user deletes their sent messages.
- This is a **known data integrity risk**: deleting a user should not destroy conversation history for remaining participants.
- The intended behavior is `ON DELETE SET NULL` on `sender_id`, so deleted users' messages are preserved with a null sender.

### C. Scope Simplifications

- Messages use a hybrid soft-delete pattern: `is_deleted BOOLEAN` + `deleted_at TIMESTAMPTZ`. This differs from the `deleted_at`-only pattern used by other modules. Both fields must be set on delete.
- No end-to-end encryption in v1.
- No real-time delivery via WebSocket in v1; clients must poll for new messages.
- No message reactions.
- No read receipts beyond `last_read_at` at the conversation level.

---

## Section 4: Inter-Module Dependencies

| Dependency | Direction | Nature |
|------------|-----------|--------|
| `users` | inbound | `sender_id`, `created_by`, `conversation_participants.user_id` all reference `users.id` |
| `media` | outbound | `messages.media_asset_id` references `media_assets` for image/video messages |
| `post` | outbound | `messages.shared_post_id` references `posts` for shared-post messages |
| `story` | outbound | `messages.shared_story_id` references `stories` for shared-story messages |
| `social` | inbound | Block relationships govern messaging permissions |
| `notification` | outbound | Intended: new messages would trigger `MESSAGE` notification creation for participants once a send-message endpoint exists. `notification_type_configs.MESSAGE` and `user_settings.notify_messages` exist for this but have no creation site today. |
| `report` | inbound | Reports can target a message via polymorphic `entity_id` |
