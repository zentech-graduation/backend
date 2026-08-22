-- Gives messages an administrative tombstone that is orthogonal to the sender's own.
--
-- The module's existing delete semantic sets is_deleted and deleted_at together and clears content
-- to a tombstone, which is irreversible by design: a sender who deletes a message destroys it and
-- the recipient keeps a "message deleted" placeholder in its place.
--
-- Administrative removal has to be reversible, so it cannot reuse those three columns. Toggling
-- is_deleted alone would also let a restore undo a sender's own deletion and put back a message
-- whose content the sender had already destroyed, which would surface as an empty message rather
-- than as the placeholder the participants expect.
--
-- admin_removed_at is therefore a second, independent tombstone. Removal sets it and preserves
-- content so a restore can return the message; restore clears it and leaves any sender deletion
-- exactly as it was. A message is hidden when either tombstone is set.

ALTER TABLE messages ADD COLUMN admin_removed_at TIMESTAMPTZ;

COMMENT ON COLUMN messages.admin_removed_at IS
    'Set by administrative removal; independent of the sender-owned is_deleted/deleted_at pair. Content is preserved so a restore can return the message.';
