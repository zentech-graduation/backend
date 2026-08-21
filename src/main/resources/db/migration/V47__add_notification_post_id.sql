-- Additive enrichment: content notifications about a comment (comment_post, reply_comment,
-- like_comment, mention_comment) carry only the comment id in entity_id, with no way to resolve
-- the post it belongs to. post_id is populated at notification-creation time from data the
-- producing consumer already has, so no read-time join against the comment table is needed.
ALTER TABLE notifications ADD COLUMN post_id UUID;
