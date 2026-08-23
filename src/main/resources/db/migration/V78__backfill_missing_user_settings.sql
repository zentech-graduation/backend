-- Gives every existing account the user_settings row the read requires.
--
-- GET and PATCH /users/me/settings answer 404 when no row holds the account's id. Every path that
-- creates an account through the server already writes one: AuthServiceImpl.register,
-- CustomOidcUserService for a first OAuth2 sign-in, and DevDataSeedServiceImpl. The gap was
-- scripts/seed-dev-data.sh, which is fixed alongside this migration, and any account created
-- before those writers existed.
--
-- Insert only. No existing row is touched, so nothing needs archiving: a row already present
-- carries the account's real preferences and the ON CONFLICT leaves it exactly as it is.
--
-- Soft-deleted accounts are included deliberately. The read is reached with the caller's own id
-- and an account can be restored, so excluding them would leave the same 404 waiting behind a
-- reinstatement.

INSERT INTO user_settings (user_id)
SELECT u.id
FROM users u
WHERE NOT EXISTS (
    SELECT 1 FROM user_settings s WHERE s.user_id = u.id
)
ON CONFLICT (user_id) DO NOTHING;
