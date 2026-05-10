# Media Module — Data Rules

**Implementation status**: Scaffolding only. No Service, Controller, or Repository Java files exist for this module.

---

## Section 1: Canonical Data

| Table | Key Columns | Notes |
|-------|-------------|-------|
| `media_assets` | `id`, `user_id`, `storage_key`, `cdn_url`, `media_type`, `mime_type`, `file_size`, `width`, `height`, `duration`, `blurhash`, `created_at` | Canonical record of every uploaded media file. `storage_key` is the Cloudflare R2 object key. `cdn_url` is the public CDN URL for rendering. |

Full rebuild is not possible. If R2 objects still exist, basic metadata (`mime_type`, `file_size`, `width`, `height`, `duration`) can be partially re-extracted. However, `blurhash`, `alt_text`, original `user_id` ownership context, and `created_at` timestamps may be unrecoverable without the original DB records.

Media records are created after a client-side upload to Cloudflare R2 via pre-signed URL. All metadata fields are submitted by the client. See `GLOBAL_RULES.md` — Media Upload Flow.

---

## Section 2: Derived Data / Cache / Projection

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
| All media metadata (`width`, `height`, `duration`, `mime_type`, `file_size`, `blurhash`) is submitted by the client after direct upload to R2 via pre-signed URL; the server does not perform server-side media inspection at upload time | `[NOT YET IMPLEMENTED]` |
| Maximum file size limits must be enforced at the API layer before issuing the pre-signed upload URL | `[NOT YET IMPLEMENTED]` |
| Accepted `mime_type` values must be validated against an allowlist (e.g., `image/jpeg`, `image/png`, `video/mp4`) | `[NOT YET IMPLEMENTED]` |
| Deleting a `media_assets` row must also delete the corresponding R2 object; do not leave orphaned objects in storage | `[NOT YET IMPLEMENTED]` |
| A media asset owned by user A must not be referenceable by user B in their posts | `[NOT YET IMPLEMENTED]` |
| **Server-side metadata validation before `media_assets` insert**: Although media files are uploaded directly to Cloudflare R2 by the client (pre-signed URL flow), the server MUST validate all client-submitted metadata before creating the `media_assets` record. Validation includes: `file_size` must be within the limit defined in `system_settings.max_media_size_mb`; `media_type` must match an accepted value (`image` or `video`); `mime_type` must be within the application's allowed MIME type list; `width` and `height` must be positive integers (for image and video); `duration` must be a non-negative integer (required for video, null for image); `storage_key` must follow the expected R2 key format and must not already exist in `media_assets`. The client is not trusted to submit correct metadata. Validation failure must reject the "upload complete" request and leave no orphaned `media_assets` record. The R2 object may remain; orphan cleanup is a separate operational concern. | `[NOT YET IMPLEMENTED]` |

### C. Scope Simplifications

- `users.avatar_url` stores the CDN URL as plain `TEXT`, not a FK to `media_assets`. This means avatar assets are not referentially tracked and can become orphaned if not explicitly deleted.
- No image processing pipeline (resizing, transcoding) is defined in v1; raw uploads are stored and served as-is.
- No virus scanning or content moderation at upload time.

---

## Section 4: Inter-Module Dependencies

| Dependency | Direction | Nature |
|------------|-----------|--------|
| `users` | inbound | Every `media_assets` row belongs to a `user_id` |
| `post` | inbound | `post_media` references `media_assets.id` for post images/videos |
| `story` | inbound | `stories.media_asset_id` references `media_assets.id` |
| `message` | inbound | `messages.media_asset_id` references `media_assets.id` for image/video messages |
