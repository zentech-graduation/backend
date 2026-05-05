# Media Module — Source of Truth

**Implementation status**: Scaffolding only. No Service, Controller, or Repository Java files exist for this module.

---

## Section 1: Source-of-Truth Data

| Table | Key Columns | Notes |
|-------|-------------|-------|
| `media_assets` | `id`, `user_id`, `storage_key`, `cdn_url`, `media_type`, `mime_type`, `file_size`, `width`, `height`, `duration`, `blurhash`, `created_at` | Canonical record of every uploaded media file. `storage_key` is the Cloudflare R2 object key. `cdn_url` is the public CDN URL for rendering. |

This table cannot be rebuilt from any other source if lost (the R2 objects may still exist, but the metadata — `blurhash`, `width`, `height`, `duration` — would be lost).

---

## Section 2: Derived / Secondary Data

| Data | Location | Rebuilt From | Rebuild Trigger |
|------|----------|--------------|-----------------|
| CDN-delivered file content | Cloudflare R2 / CDN edge | Original upload; rebuilding `cdn_url` requires re-generating a signed URL or re-uploading | Manual intervention |
| Avatar URLs on `users.avatar_url` | `users` table | `media_assets.cdn_url` by convention (no FK) | Manual update if CDN URL changes |

---

## Section 3: Business Rules

### A. Rules Enforced by the Database

| Rule | Enforced By |
|------|-------------|
| `storage_key` must be globally unique (no two assets share the same R2 key) | `UNIQUE NOT NULL` on `storage_key` |
| `media_type` must be one of `'image'`, `'video'` | `media_type` enum |
| `file_size` must be greater than 0 | `CHECK (file_size > 0)` |
| `width` and `height` must be positive if provided | `CHECK (width > 0)`, `CHECK (height > 0)` |
| `duration` must be non-negative if provided (0 is a valid minimum for very short video) | `CHECK (duration >= 0)` |
| Deleting a user cascades to their `media_assets` rows | `ON DELETE CASCADE` on `media_assets.user_id` |
| `post_media` and `stories` reference `media_assets` without cascade (asset deletion is independent of post deletion) | `REFERENCES media_assets(id)` with no ON DELETE clause |

### B. Rules Enforced by Application Code

| Rule | Service / Component |
|------|---------------------|
| File upload must generate a unique `storage_key` (e.g., UUID-based path) before writing to R2 | `[NOT YET IMPLEMENTED]` |
| `cdn_url` is derived from `storage_key` using the configured CDN base URL; it must not be user-supplied | `[NOT YET IMPLEMENTED]` |
| `width`, `height` are extracted from the image/video after upload via server-side processing | `[NOT YET IMPLEMENTED]` |
| `duration` is extracted from video metadata after upload | `[NOT YET IMPLEMENTED]` |
| `blurhash` is computed server-side from the image after upload; it must not be user-supplied | `[NOT YET IMPLEMENTED]` |
| Maximum file size limits must be enforced at the API layer before upload to R2 | `[NOT YET IMPLEMENTED]` |
| Accepted `mime_type` values must be validated against an allowlist (e.g., `image/jpeg`, `image/png`, `video/mp4`) | `[NOT YET IMPLEMENTED]` |
| Deleting a `media_assets` row must also delete the corresponding R2 object; do not leave orphaned objects in storage | `[NOT YET IMPLEMENTED]` |
| A media asset owned by user A must not be referenceable by user B in their posts | `[NOT YET IMPLEMENTED]` |

### C. Scope Simplifications

- `users.avatar_url` stores the CDN URL as plain `TEXT`, not a FK to `media_assets`. This means avatar assets are not referentially tracked and can become orphaned if not explicitly deleted.
- No image processing pipeline (resizing, transcoding) is defined in v1; raw uploads are stored and served as-is.
- No virus scanning or content moderation at upload time.
- No pre-signed upload URLs — exact upload flow (direct-to-R2 vs server-side) is not yet defined.

---

## Section 4: Inter-Module Dependencies

| Dependency | Direction | Nature |
|------------|-----------|--------|
| `users` | inbound | Every `media_assets` row belongs to a `user_id` |
| `post` | inbound | `post_media` references `media_assets.id` for post images/videos |
| `story` | inbound | `stories.media_asset_id` references `media_assets.id` |
| `message` | inbound | `messages.media_asset_id` references `media_assets.id` for image/video messages |
