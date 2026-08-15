# Message Module - Data Rules

**Implementation status**: Fully implemented.
Conversation and group lifecycle, message send/history/delete/read, notification fan-out, and real-time WebSocket delivery are all in place.

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
| Deleting a user cascades to their `conversation_participants` rows | `ON DELETE CASCADE` on FK to `users` |

### B. Rules Enforced by Application Code

| Rule | Service / Component |
|------|---------------------|
| A user may only send messages to conversations they are an active participant of (`left_at IS NULL`) | `MessageServiceImpl.sendMessage`, `MessageServiceImpl.listHistory`, `MessageServiceImpl.markRead` |
| For a 1-1 conversation (`is_group = FALSE`), there must be exactly 2 participants; a duplicate request reuses the existing conversation | `ConversationServiceImpl.createDirectConversation` - enforced by construction (the only path that creates a non-group conversation, always inserting the actor plus exactly one target); reactivates the caller's membership if they had left |
| A blocked user may not initiate or reply to messages with the blocker | Initiation enforced by `ConversationServiceImpl.assertNotBlocked`, called from `createDirectConversation`, `createGroupConversation`, and `addParticipants`; replies enforced by `MessageServiceImpl.assertNotBlockedForDirectMessage`, called from `sendMessage` |
| Message soft-delete sets `is_deleted = TRUE` and `deleted_at = NOW()`, and clears `content` to a tombstone | `MessageServiceImpl.deleteMessage` |
| Only the message sender may delete their own message | `MessageServiceImpl.deleteMessage` |
| `last_read_at` on `conversation_participants` is updated when the user reads the conversation | `MessageServiceImpl.markRead` |
| A message of type `'post_share'` must have `shared_post_id` set; `'story_share'` must have `shared_story_id` set | `MessageServiceImpl.sendMessage` payload validation |
| A message of type `'image'` or `'video'` must have `media_asset_id` set | `MessageServiceImpl.sendMessage` payload validation |
| Group admins may add/remove participants and update `group_name` / `group_avatar_url`; the last active admin leaving or being removed promotes the oldest remaining member | `ConversationServiceImpl.addParticipants` / `removeParticipant` / `updateGroup`, all gated by `requireGroupAdmin` |
| Sending a message generates a `message` notification for every other active participant | `MessageNotificationConsumer` |
| `user_settings.allow_message_requests` governs whether non-followers can initiate a conversation | `ConversationServiceImpl.assertMessageRequestAllowed`, called from `createDirectConversation` |
| A live WebSocket SUBSCRIBE to a conversation's topic is rejected unless the subscriber is an active participant | `MessageWebSocketAuthInterceptor` |

**`sender_id` cascade behavior** `[FIXED - V32]`:
`sender_id` now uses `ON DELETE SET NULL` instead of `ON DELETE CASCADE` (migration V32).
Deleting a user's account preserves their past messages for the remaining participants instead of destroying them; a null `sender_id` renders as a deleted-user placeholder in message responses.

### C. Scope Simplifications

- Messages use a hybrid soft-delete pattern: `is_deleted BOOLEAN` + `deleted_at TIMESTAMPTZ`. This differs from the `deleted_at`-only pattern used by other modules. Both fields must be set on delete.
- No end-to-end encryption in v1.
- No message reactions.
- No read receipts beyond `last_read_at` at the conversation level.
- Group chat is gated by the `app.message.group-chat-enabled` configuration property, not the `feature_flags.group_chat` table row.
  That table row is decorative; there is no runtime `feature_flags` reader in this codebase.
- Real-time delivery uses a single in-process `SimpleBroker` (no distributed STOMP relay), so delivery is best-effort per instance; a client recovers any missed event through the message history endpoint on reconnect.

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
