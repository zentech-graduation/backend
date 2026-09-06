# Media Module — Data Rules

**Implementation status**: Pre-signed upload URL issuance and upload-complete persistence are implemented. Storage-object deletion and media reference checks from post/story/message modules are not implemented yet.

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
| File upload must generate a unique `storage_key` (e.g., UUID-based path) before writing to R2 | `MediaStorageKeyGenerator`, `MediaServiceImpl` |
| `cdn_url` is derived from `storage_key` using the configured CDN base URL; it must not be user-supplied | `MediaServiceImpl` |
| All media metadata (`width`, `height`, `duration`, `mime_type`, `file_size`, `blurhash`) is submitted by the client after direct upload to R2 via pre-signed URL; the server does not perform server-side media inspection at upload time | `MediaController`, `MediaServiceImpl` |
| Maximum file size limits must be enforced at the API layer before issuing the pre-signed upload URL | `MediaMetadataValidator`, `MediaServiceImpl` |
| Accepted `mime_type` values must be validated against an allowlist (e.g., `image/jpeg`, `image/png`, `video/mp4`) | `MediaMetadataValidator` |
| Deleting a `media_assets` row must also delete the corresponding R2 object; do not leave orphaned objects in storage | `[NOT YET IMPLEMENTED]` |
| A media asset owned by user A must not be referenceable by user B in their posts | `[NOT YET IMPLEMENTED]` |
| **Server-side metadata validation before `media_assets` insert**: Although media files are uploaded directly to Cloudflare R2 by the client (pre-signed URL flow), the server MUST validate all client-submitted metadata before creating the `media_assets` record. Validation includes: `file_size` must be within the limit defined in `system_settings.max_media_size_mb`; `media_type` must match an accepted value (`image` or `video`); `mime_type` must be within the application's allowed MIME type list; `width` and `height` must be positive integers (for image and video); `duration` must be a non-negative integer (required for video, null for image); `storage_key` must follow the expected R2 key format and must not already exist in `media_assets`. The client is not trusted to submit correct metadata. Validation failure must reject the "upload complete" request and leave no orphaned `media_assets` record. The R2 object may remain; orphan cleanup is a separate operational concern. | `MediaMetadataValidator`, `MediaServiceImpl` |
| Successful upload-complete writes the `media_assets` row synchronously and records a `media.uploaded.v1` outbox event in the same transaction. The event payload contains only `mediaAssetId`, `userId`, and `mediaType`. | `MediaServiceImpl`, `MediaEventServiceImpl` |
| **Stored-object verification before `media_assets` insert**: upload-complete must confirm with object storage that an object exists under the submitted `storage_key` before the row is written. Absent object rejects with 422. The stored object's `Content-Length` must equal the submitted `file_size` and its `Content-Type` must equal the submitted `mime_type`; either mismatch rejects with 422. This is a header check only, never a body fetch, so it does not make the server an inspector of media content. | `MediaServiceImpl`, `ObjectStorageMetadataService` |
| **Verification fails closed**: if object storage is unreachable or errors, upload-complete rejects with 503 and writes no row. A row is never created on an unverified key, so no lifecycle or status column is needed to represent a pending asset. This also removes the need for a registry of previously issued storage keys, because a key the server never presigned is also a key nothing was uploaded to. | `MediaServiceImpl`, `R2ObjectStorageMetadataService` |

### B1. Video duration is advisory, file size is verified

These two limits look alike and are not, so a reader should not assume one is as strong as the other.

**Video duration is advisory.**
`app.media.max-video-duration-seconds` (default 180) rejects an upload-complete request whose declared `duration` exceeds it.
Duration is client-supplied metadata and the server never opens the file, so the limit stops an honest client and does nothing against a dishonest one.
A client that declares 10 and uploads a 20-minute video is accepted.
It is still worth enforcing, because it gives the composer a rule to enforce ahead of upload and a message to show, but it must never be described as a guarantee.

Making it enforceable would require the server to read the uploaded bytes and decode the container to extract the real duration, either inline at upload-complete or in a background job that corrects or removes the row afterwards.
That reverses the decision in `GLOBAL_RULES.md` section 7 that the server performs no server-side media inspection, and is its own project.

**File size is genuinely verified.**
The stored-object probe compares the object's actual `Content-Length` against the declared `file_size` and rejects a mismatch with 422.
The presigned URL also signs the content length, so R2 itself refuses a body of any other size.
A lie about file size is therefore caught; a lie about duration is not.

**The size ceiling is global, not per media type.**
`system_settings.max_media_size_mb` (default 100) is applied identically to image and video, so a 100MB image is accepted even though the ceiling is sized for video.
This is a known consequence of a single setting and is recorded here rather than changed.

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
