-- Composite (recipient_id, created_at DESC, id DESC) index so the notification row-value keyset
-- comparison resolves as an exact index seek instead of a filter over the recipient's rows.

CREATE INDEX idx_notifications_recipient_created_id
    ON notifications (recipient_id, created_at DESC, id DESC);
