#!/usr/bin/env python3
"""Synthetic persona seed for the recommendation module demo.

Generates a deterministic dataset (random.seed + UUIDv5, safe to re-run):
  - 8 topics, 200 regular users (25 per topic), 2 demo accounts with disjoint personas
  - 800 text posts (100 per topic), hashtags, same-topic follow graph
  - ~28k interactions (60% like / 25% save / 15% comment), 80/15/5 topic affinity

Subcommands:
  sql     Write seed.sql for the app database (fresh database only).
  push    Push users/items/feedback into Gorse over REST (idempotent; doubles as
          the Gorse backfill/rebuild tool).
  verify  After a fit cycle, check persona separation of the two demo accounts.
  all     sql + push.

Credentials for every seeded user: password "Demo@Pass123" (bcrypt cost 12, matching
BCryptPasswordEncoder(12)), email "<username>@seed.local", email verified, active.

Uses only the Python standard library. Gorse connection comes from --gorse-url /
--api-key or the APP_GORSE_BASE_URL / GORSE_API_KEY environment variables.
"""

import argparse
import json
import os
import random
import sys
import urllib.request
import uuid
from datetime import datetime, timedelta, timezone

TOPICS = ["football", "travel", "cooking", "fashion", "tech", "music", "fitness", "art"]
USERS_PER_TOPIC = 25
POSTS_PER_USER = 4
DEMO_USERS = {
    "demo_an": ("football", "travel"),
    "demo_binh": ("cooking", "fashion"),
}
BCRYPT_HASH = "$2a$12$g3TV1x1algWmmwIYVGFho.KdJ4k6yBUpvwiR7ka.0OTGCoiGbR0hu"
NAMESPACE = uuid.UUID("6b7d3c2a-9e4f-4a1b-8c5d-2f0e1a3b4c5d")
NOW = datetime(2026, 7, 26, 12, 0, 0, tzinfo=timezone.utc)

CAPTION_TEMPLATES = [
    "Sharing my latest {t} moment #{t}",
    "Can't get enough of {t} today #{t}",
    "A little {t} inspiration for everyone #{t}",
    "Weekend {t} vibes #{t}",
    "My honest thoughts on {t} right now #{t}",
]
COMMENT_TEXTS = [
    "Love this!",
    "Great post, thanks for sharing.",
    "This is so inspiring.",
    "Totally agree with you.",
    "Amazing, keep it up!",
]


def uid(kind, key):
    return str(uuid.uuid5(NAMESPACE, f"{kind}:{key}"))


def ts(dt):
    return dt.strftime("%Y-%m-%d %H:%M:%S+00")


def iso(dt):
    return dt.strftime("%Y-%m-%dT%H:%M:%SZ")


def build_dataset():
    rng = random.Random(42)
    users = []
    for t_idx, topic in enumerate(TOPICS):
        secondary = TOPICS[(t_idx + 3) % len(TOPICS)]
        for i in range(USERS_PER_TOPIC):
            name = f"{topic}_{i:02d}"
            users.append(
                {
                    "id": uid("user", name),
                    "username": name,
                    "primary": topic,
                    "secondary": secondary,
                    "demo": False,
                }
            )
    for name, (primary, secondary) in DEMO_USERS.items():
        users.append(
            {
                "id": uid("user", name),
                "username": name,
                "primary": primary,
                "secondary": secondary,
                "demo": True,
            }
        )

    posts = []
    posts_by_topic = {t: [] for t in TOPICS}
    for u in users:
        if u["demo"]:
            continue
        for p in range(POSTS_PER_USER):
            key = f"{u['username']}:{p}"
            created = NOW - timedelta(hours=rng.uniform(24, 90 * 24))
            post = {
                "id": uid("post", key),
                "user_id": u["id"],
                "topic": u["primary"],
                "caption": rng.choice(CAPTION_TEMPLATES).format(t=u["primary"]),
                "created": created,
            }
            posts.append(post)
            posts_by_topic[u["primary"]].append(post)

    hashtags = [{"id": uid("hashtag", t), "name": t} for t in TOPICS]

    follows = set()
    for u in users:
        same_topic = [
            o for o in users if not o["demo"] and o["primary"] == u["primary"] and o["id"] != u["id"]
        ]
        count = 15 if u["demo"] else rng.randint(8, 15)
        for target in rng.sample(same_topic, min(count, len(same_topic))):
            follows.add((u["id"], target["id"]))
        if u["demo"]:
            sec = [o for o in users if not o["demo"] and o["primary"] == u["secondary"]]
            for target in rng.sample(sec, 5):
                follows.add((u["id"], target["id"]))

    likes, saves, comments = set(), set(), {}
    feedback = []

    def pick_post(u):
        roll = rng.random()
        if roll < 0.80:
            pool = posts_by_topic[u["primary"]]
        elif roll < 0.95:
            pool = posts_by_topic[u["secondary"]]
        else:
            pool = posts
        post = rng.choice(pool)
        return None if post["user_id"] == u["id"] else post

    for u in users:
        n = 120 if u["demo"] else rng.randint(100, 180)
        for _ in range(n):
            post = pick_post(u)
            if post is None:
                continue
            pair = (u["id"], post["id"])
            when = max(
                post["created"] + timedelta(hours=1),
                NOW - timedelta(hours=rng.uniform(1, 60 * 24)),
            )
            when = min(when, NOW - timedelta(minutes=1))
            roll = rng.random()
            if roll < 0.60:
                bucket, ftype = likes, "like"
            elif roll < 0.85:
                bucket, ftype = saves, "save"
            else:
                bucket, ftype = comments, "comment"
            if bucket is comments:
                if pair in comments:
                    continue
                comments[pair] = (rng.choice(COMMENT_TEXTS), when)
            else:
                if pair in bucket:
                    continue
                bucket.add(pair)
            feedback.append((ftype, u["id"], post["id"], when))

    return users, posts, hashtags, follows, likes, saves, comments, feedback


def sql_escape(text):
    return text.replace("'", "''")


def emit_sql(path, users, posts, hashtags, follows, likes, saves, comments):
    lines = ["BEGIN;"]

    def batch(rows, prefix, fmt, size=1000):
        for start in range(0, len(rows), size):
            chunk = rows[start : start + size]
            lines.append(prefix)
            lines.append(",\n".join(fmt(r) for r in chunk) + ";")

    batch(
        users,
        "INSERT INTO users (id, username, email, display_name, role, status, is_private) VALUES",
        lambda u: (
            f"('{u['id']}', '{u['username']}', '{u['username']}@seed.local', "
            f"'{u['username'].replace('_', ' ').title()}', 'user', 'active', FALSE)"
        ),
    )
    batch(
        users,
        "INSERT INTO user_credentials (user_id, password_hash, email_verified, email_verified_at) VALUES",
        lambda u: f"('{u['id']}', '{BCRYPT_HASH}', TRUE, NOW())",
    )
    batch(
        posts,
        "INSERT INTO posts (id, user_id, caption, post_type, status, created_at, updated_at) VALUES",
        lambda p: (
            f"('{p['id']}', '{p['user_id']}', '{sql_escape(p['caption'])}', 'text', "
            f"'published', '{ts(p['created'])}', '{ts(p['created'])}')"
        ),
    )
    batch(
        hashtags,
        "INSERT INTO hashtags (id, name) VALUES",
        lambda h: f"('{h['id']}', '{h['name']}')",
    )
    tag_by_topic = {h["name"]: h["id"] for h in hashtags}
    batch(
        posts,
        "INSERT INTO post_hashtags (post_id, hashtag_id) VALUES",
        lambda p: f"('{p['id']}', '{tag_by_topic[p['topic']]}')",
    )
    batch(
        sorted(follows),
        "INSERT INTO follows (follower_id, following_id, status) VALUES",
        lambda f: f"('{f[0]}', '{f[1]}', 'accepted')",
    )
    like_rows = sorted(likes)
    batch(
        like_rows,
        "INSERT INTO post_likes (user_id, post_id) VALUES",
        lambda r: f"('{r[0]}', '{r[1]}')",
    )
    batch(
        sorted(saves),
        "INSERT INTO post_saves (user_id, post_id) VALUES",
        lambda r: f"('{r[0]}', '{r[1]}')",
    )
    comment_rows = sorted(comments.items())
    batch(
        comment_rows,
        "INSERT INTO comments (id, post_id, user_id, content, created_at, updated_at) VALUES",
        lambda r: (
            f"('{uid('comment', r[0][0] + r[0][1])}', '{r[0][1]}', '{r[0][0]}', "
            f"'{sql_escape(r[1][0])}', '{ts(r[1][1])}', '{ts(r[1][1])}')"
        ),
    )
    lines.append("COMMIT;")
    with open(path, "w", encoding="utf-8") as fh:
        fh.write("\n".join(lines) + "\n")


def gorse_request(base, key, method, path, body=None):
    req = urllib.request.Request(
        base.rstrip("/") + path,
        data=json.dumps(body).encode() if body is not None else None,
        method=method,
        headers={
            "Content-Type": "application/json",
            "X-API-Key": key,
            "X-API-Version": "2",
        },
    )
    with urllib.request.urlopen(req, timeout=30) as resp:
        return json.loads(resp.read())


def push(base, key, users, posts, feedback):
    gorse_users = [
        {"UserId": u["id"], "Labels": [u["primary"], u["secondary"]], "Comment": u["username"]}
        for u in users
    ]
    gorse_items = [
        {
            "ItemId": p["id"],
            "IsHidden": False,
            "Categories": [],
            "Labels": [p["topic"]],
            "Timestamp": iso(p["created"]),
            "Comment": "",
        }
        for p in posts
    ]
    gorse_feedback = [
        {"FeedbackType": f[0], "UserId": f[1], "ItemId": f[2], "Timestamp": iso(f[3])}
        for f in feedback
    ]
    for start in range(0, len(gorse_users), 1000):
        gorse_request(base, key, "POST", "/api/users", gorse_users[start : start + 1000])
    for start in range(0, len(gorse_items), 1000):
        gorse_request(base, key, "POST", "/api/items", gorse_items[start : start + 1000])
    for start in range(0, len(gorse_feedback), 1000):
        gorse_request(base, key, "POST", "/api/feedback", gorse_feedback[start : start + 1000])
    print(
        f"pushed {len(gorse_users)} users, {len(gorse_items)} items, "
        f"{len(gorse_feedback)} feedback rows"
    )


def verify(base, key, users, posts):
    topic_of = {p["id"]: p["topic"] for p in posts}
    results = {}
    for name in DEMO_USERS:
        user_id = uid("user", name)
        scores = gorse_request(base, key, "GET", f"/api/recommend/{user_id}?n=30")
        ids = [s["Id"] if isinstance(s, dict) else s for s in scores]
        primary, secondary = DEMO_USERS[name]
        on_persona = sum(1 for i in ids if topic_of.get(i) in (primary, secondary))
        results[name] = (ids, on_persona)
        print(f"{name}: {len(ids)} recs, {on_persona} on persona ({primary}/{secondary})")
    id_sets = [set(r[0]) for r in results.values()]
    overlap = len(id_sets[0] & id_sets[1])
    smaller = min(len(s) for s in id_sets) or 1
    print(f"overlap between demo accounts: {overlap}/{smaller} ({100 * overlap // smaller}%)")
    ok = all(len(r[0]) >= 10 and r[1] >= 0.7 * len(r[0]) for r in results.values())
    ok = ok and overlap < 0.2 * smaller
    print("PASS" if ok else "FAIL: separation below thresholds")
    return 0 if ok else 1


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=["sql", "push", "verify", "all"])
    parser.add_argument("--sql-out", default="seed.sql")
    parser.add_argument("--gorse-url", default=os.environ.get("APP_GORSE_BASE_URL", "http://localhost:8088"))
    parser.add_argument("--api-key", default=os.environ.get("GORSE_API_KEY", ""))
    args = parser.parse_args()

    users, posts, hashtags, follows, likes, saves, comments, feedback = build_dataset()
    print(
        f"dataset: {len(users)} users, {len(posts)} posts, {len(follows)} follows, "
        f"{len(likes)} likes, {len(saves)} saves, {len(comments)} comments, "
        f"{len(feedback)} feedback"
    )
    if args.command in ("sql", "all"):
        emit_sql(args.sql_out, users, posts, hashtags, follows, likes, saves, comments)
        print(f"wrote {args.sql_out}")
    if args.command in ("push", "all"):
        push(args.gorse_url, args.api_key, users, posts, feedback)
    if args.command == "verify":
        sys.exit(verify(args.gorse_url, args.api_key, users, posts))


if __name__ == "__main__":
    main()
