"""Verify that every media/avatar/banner reference in users.json and posts.json
resolves to a real media_manifest.json id, and that every manifest id is
referenced by at least one of those two files.

Usage: python scripts/verify-seed-media-integrity.py
Exits non-zero if unresolved_references or orphaned_manifest_entries is non-empty.
"""

import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SEED = ROOT / "src" / "main" / "resources" / "seed"

manifest = json.loads((SEED / "media" / "media_manifest.json").read_text(encoding="utf-8"))
users_doc = json.loads((SEED / "users.json").read_text(encoding="utf-8"))
posts_doc = json.loads((SEED / "content" / "posts.json").read_text(encoding="utf-8"))

manifest_ids = set()
for key in ("images", "videos", "avatars", "banners"):
    for entry in manifest[key]:
        manifest_ids.add(entry["id"])

referenced_ids = set()
for user in users_doc["users"]:
    if user.get("avatar_media_ref"):
        referenced_ids.add(user["avatar_media_ref"])
    if user.get("banner_media_ref"):
        referenced_ids.add(user["banner_media_ref"])

for post in posts_doc["posts"]:
    for ref in post.get("media_refs", []):
        referenced_ids.add(ref)

unresolved_references = sorted(referenced_ids - manifest_ids)
orphaned_manifest_entries = sorted(manifest_ids - referenced_ids)

print(f"manifest_ids: {len(manifest_ids)}")
print(f"referenced_ids: {len(referenced_ids)}")
print(f"unresolved_references: {len(unresolved_references)}")
if unresolved_references:
    print(unresolved_references)
print(f"orphaned_manifest_entries: {len(orphaned_manifest_entries)}")
if orphaned_manifest_entries:
    print(orphaned_manifest_entries)

if unresolved_references or orphaned_manifest_entries:
    sys.exit(1)

print("OK: 0 unresolved references, 0 orphaned manifest entries")
