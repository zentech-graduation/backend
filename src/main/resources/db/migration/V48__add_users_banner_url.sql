-- Adds a banner (cover image) URL alongside the existing avatar_url, following the same
-- CDN-URL-string convention: nullable, no default, cleared by setting NULL.
ALTER TABLE users ADD COLUMN banner_url TEXT;
