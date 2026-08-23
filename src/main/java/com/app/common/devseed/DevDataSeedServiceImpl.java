package com.app.common.devseed;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

/**
 * Seeds a development database directly through SQL.
 *
 * <p>Accounts are created with a real BCrypt hash from the application's own encoder, so they log
 * in exactly like registered users, and with the email credential already verified. Media is
 * referenced by real, externally hosted URLs (Picsum images and public sample videos) stored as the
 * asset's CDN URL, so it renders in the interface without any upload. Denormalised counters are
 * never written here; inserting into the source tables lets the database triggers maintain them.
 */
@Service
@Profile("dev")
@RequiredArgsConstructor
public class DevDataSeedServiceImpl implements DevDataSeedService {

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;

    private static final SecureRandom DEV_SEED_RANDOM = new SecureRandom();

    private static final String PASSWORD = "Password123!";
    private static final String DOMAIN = "luvax.test";
    private static final String REVIEWER_USERNAME = "JohnDoe";
    private static final String REVIEWER_EMAIL = "johndoe@luvax.test";
    private static final String REVIEWER_NAME = "John Doe";
    private static final String BLURHASH = "LKO2?U%2Tw=w]~RBVZRi};RPxuwH";

    private static final String[][] PEOPLE = {
        {"Mara Vance", "photographer chasing north light"},
        {"Idris Kone", "film stills, grain over pixels"},
        {"Sol Reyes", "surf, salt, and slow mornings"},
        {"Noa Burgi", "type designer, serifs mostly"},
        {"Lea Petrov", "climbing dirtbag with a camera"},
        {"Jae Okoro", "street photography, lagos and london"},
        {"Ren Kato", "ceramics and quiet rooms"},
        {"Ava Lindqvist", "cold water swimmer"},
        {"Tomas Feld", "architecture, concrete, shadow"},
        {"Priya Nair", "botanist, plants over people"},
        {"Kai Mwangi", "trail runner, 6am club"},
        {"Elena Rossi", "pasta, light, and my nonna"},
        {"Yuki Tanabe", "minimal interiors and tea"},
        {"Freya Holm", "arctic seasons, long exposures"},
        {"Mateo Cruz", "skate, grain, and the golden hour"},
        {"Amara Okafor", "colour theory in the wild"},
    };

    private static final String[] CAPTIONS = {
        "ridge line before the storm rolled in",
        "morning, window, coffee",
        "grain is the texture of memory",
        "found this light and had to stop",
        "the sea was doing something quiet today",
        "concrete and shadow, my favourite pairing",
        "the colour of 6am",
        "brutalist stairwell, midday",
        "the market was alive tonight",
        "cold water, clear head",
        "golden hour did the work for me",
        "old camera, new eyes",
        "keeping this one simple",
        "the city exhaling at dusk",
    };

    private static final String[] TAGS = {
        "photography",
        "film",
        "35mm",
        "travel",
        "architecture",
        "nature",
        "portrait",
        "street",
        "minimal",
        "goldenhour",
        "analog",
        "ocean",
        "mountains",
        "city",
    };

    private static final String[] COMMENTS = {
        "this is stunning", "the light here is unreal", "saved instantly",
        "the composition though", "peak grain, love it", "need a print of this",
        "where is this?", "the mood is everything", "colours are singing",
        "you have such an eye", "the framing is perfect", "goosebumps",
    };

    private static final String[] REPLIES = {
        "thank you!",
        "means a lot",
        "haha appreciate it",
        "you're too kind",
        "shot on film, no edits",
        "somewhere up north",
        "spot metered for the highlights",
    };

    private static final int[][] IMG_SHAPES = {
        {1080, 1080},
        {1080, 1350},
        {1350, 1080},
        {1080, 1620},
        {1620, 1080},
        {1080, 1920},
        {1920, 1080},
    };

    // url, width, height, duration(seconds) - small public sample videos that render directly.
    private static final Object[][] VIDEOS = {
        {"https://download.samplelib.com/mp4/sample-5s.mp4", 1280, 720, 5},
        {
            "https://test-videos.co.uk/vids/bigbuckbunny/mp4/h264/360/Big_Buck_Bunny_360_10s_1MB.mp4",
            640,
            360,
            10
        },
        {"https://www.w3schools.com/html/mov_bbb.mp4", 320, 176, 10},
    };

    @Override
    @Transactional
    public DevSeedResult seed() {
        Integer existing =
                jdbc.queryForObject(
                        "SELECT count(*) FROM users WHERE email = ?",
                        Integer.class,
                        REVIEWER_EMAIL);
        if (existing != null && existing > 0) {
            return DevSeedResult.alreadySeeded();
        }

        String hash = passwordEncoder.encode(PASSWORD);
        SecureRandom rnd = DEV_SEED_RANDOM;

        List<UUID> userIds = new ArrayList<>();
        for (int i = 0; i < PEOPLE.length; i++) {
            String name = PEOPLE[i][0];
            String bio = PEOPLE[i][1];
            String username = handle(name);
            UUID id = UUID.randomUUID();
            String banner = (i % 3 == 0) ? picsum("bn-" + i, 1620, 1080) : null;
            insertUser(
                    id,
                    username,
                    username + "@" + DOMAIN,
                    name,
                    bio,
                    avatar("av-" + i),
                    banner,
                    hash);
            userIds.add(id);
        }

        UUID reviewerId = UUID.randomUUID();
        insertUser(
                reviewerId,
                REVIEWER_USERNAME,
                REVIEWER_EMAIL,
                REVIEWER_NAME,
                "here to see everything",
                avatar("reviewer"),
                null,
                hash);

        List<UUID> postIds = new ArrayList<>();
        List<UUID> postOwners = new ArrayList<>();
        int postCount = 0;
        int mediaCount = 0;
        for (int u = 0; u < userIds.size(); u++) {
            UUID owner = userIds.get(u);
            int n = 3 + rnd.nextInt(4);
            for (int j = 0; j < n; j++) {
                double roll = rnd.nextDouble();
                String type =
                        roll < 0.22
                                ? "carousel"
                                : roll < 0.32 ? "video" : roll < 0.38 ? "text" : "image";
                UUID postId = insertPost(owner, caption(rnd), type);
                if ("image".equals(type)) {
                    int[] s = IMG_SHAPES[rnd.nextInt(IMG_SHAPES.length)];
                    linkMedia(postId, insertImage(owner, "p-" + u + "-" + j, s), 0);
                    mediaCount++;
                } else if ("carousel".equals(type)) {
                    int frames = 2 + rnd.nextInt(3);
                    for (int f = 0; f < frames; f++) {
                        int[] s = IMG_SHAPES[rnd.nextInt(IMG_SHAPES.length)];
                        linkMedia(postId, insertImage(owner, "p-" + u + "-" + j + "-" + f, s), f);
                        mediaCount++;
                    }
                } else if ("video".equals(type)) {
                    linkMedia(postId, insertVideo(owner, rnd), 0);
                    mediaCount++;
                }
                postIds.add(postId);
                postOwners.add(owner);
                postCount++;
            }
        }

        int follows = 0;
        for (UUID follower : userIds) {
            int k = 6 + rnd.nextInt(7);
            for (int t = 0; t < k; t++) {
                UUID target = userIds.get(rnd.nextInt(userIds.size()));
                if (!target.equals(follower)) {
                    follows += insertFollow(follower, target);
                }
            }
        }
        int reviewerFollows = 0;
        for (UUID target : userIds) {
            reviewerFollows += insertFollow(reviewerId, target);
        }

        int postLikes = 0;
        int saves = 0;
        int comments = 0;
        int replies = 0;
        int commentLikes = 0;
        for (int p = 0; p < postIds.size(); p++) {
            UUID postId = postIds.get(p);
            UUID owner = postOwners.get(p);
            int likers = 3 + rnd.nextInt(10);
            for (int l = 0; l < likers; l++) {
                postLikes += insertPostLike(userIds.get(rnd.nextInt(userIds.size())), postId);
            }
            int savers = rnd.nextInt(3);
            for (int s = 0; s < savers; s++) {
                saves += insertPostSave(userIds.get(rnd.nextInt(userIds.size())), postId);
            }
            int threads = 2 + rnd.nextInt(4);
            for (int c = 0; c < threads; c++) {
                UUID author = userIds.get(rnd.nextInt(userIds.size()));
                UUID commentId =
                        insertComment(
                                postId,
                                author,
                                COMMENTS[rnd.nextInt(COMMENTS.length)],
                                null,
                                null,
                                0);
                comments++;
                if (rnd.nextDouble() < 0.5) {
                    insertComment(
                            postId,
                            owner,
                            REPLIES[rnd.nextInt(REPLIES.length)],
                            commentId,
                            commentId,
                            1);
                    replies++;
                }
                int clk = rnd.nextInt(4);
                for (int k = 0; k < clk; k++) {
                    UUID liker = userIds.get(rnd.nextInt(userIds.size()));
                    if (!liker.equals(author)) {
                        commentLikes += insertCommentLike(liker, commentId);
                    }
                }
            }
        }

        String summary =
                String.format(
                        "%d users + reviewer, %d posts, %d media, %d follows, %d post-likes, %d saves,"
                                + " %d comments, %d replies, %d comment-likes",
                        userIds.size(),
                        postCount,
                        mediaCount,
                        follows + reviewerFollows,
                        postLikes,
                        saves,
                        comments,
                        replies,
                        commentLikes);
        return new DevSeedResult(
                false, summary, REVIEWER_USERNAME, REVIEWER_EMAIL, PASSWORD, reviewerFollows);
    }

    private void insertUser(
            UUID id,
            String username,
            String email,
            String name,
            String bio,
            String avatarUrl,
            String bannerUrl,
            String hash) {
        jdbc.update(
                "INSERT INTO users (id, username, email, display_name, bio, avatar_url, banner_url,"
                        + " is_verified, status) VALUES (?, ?, ?, ?, ?, ?, ?, true, 'active')",
                id,
                username,
                email,
                name,
                bio,
                avatarUrl,
                bannerUrl);
        jdbc.update(
                "INSERT INTO user_credentials (user_id, password_hash, email_verified,"
                        + " email_verified_at) VALUES (?, ?, true, now())",
                id,
                hash);
        // Mirrors AuthServiceImpl.register: every real signup gets a settings row, so a
        // dev-seeded account must too, or GET/PATCH /users/me/settings 404s for it.
        jdbc.update("INSERT INTO user_settings (user_id) VALUES (?)", id);
    }

    private UUID insertImage(UUID userId, String seed, int[] shape) {
        return insertMedia(
                userId,
                picsum(seed, shape[0], shape[1]),
                "image",
                "image/jpeg",
                shape[0],
                shape[1],
                null);
    }

    private UUID insertVideo(UUID userId, SecureRandom rnd) {
        Object[] v = VIDEOS[rnd.nextInt(VIDEOS.length)];
        return insertMedia(
                userId, (String) v[0], "video", "video/mp4", (int) v[1], (int) v[2], (int) v[3]);
    }

    private UUID insertMedia(
            UUID userId,
            String cdnUrl,
            String mediaType,
            String mimeType,
            int width,
            int height,
            Integer duration) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO media_assets (id, user_id, storage_key, cdn_url, media_type, mime_type,"
                        + " file_size, width, height, duration, blurhash)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id,
                userId,
                "dev-seed/" + id,
                cdnUrl,
                mediaType,
                mimeType,
                250_000L,
                width,
                height,
                duration,
                BLURHASH);
        return id;
    }

    private UUID insertPost(UUID userId, String caption, String postType) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO posts (id, user_id, caption, post_type, status)"
                        + " VALUES (?, ?, ?, ?, 'published')",
                id,
                userId,
                caption,
                postType);
        return id;
    }

    private void linkMedia(UUID postId, UUID mediaAssetId, int position) {
        jdbc.update(
                "INSERT INTO post_media (post_id, media_asset_id, position) VALUES (?, ?, ?)",
                postId,
                mediaAssetId,
                (short) position);
    }

    private UUID insertComment(
            UUID postId, UUID userId, String content, UUID parentId, UUID rootId, int depth) {
        UUID id = UUID.randomUUID();
        // A top-level comment roots its own subtree; a reply points at the root above it.
        UUID root = (rootId != null) ? rootId : id;
        jdbc.update(
                "INSERT INTO comments (id, post_id, user_id, content, parent_id, root_id, depth)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?)",
                id,
                postId,
                userId,
                content,
                parentId,
                root,
                (short) depth);
        return id;
    }

    private int insertFollow(UUID follower, UUID following) {
        return jdbc.update(
                "INSERT INTO follows (follower_id, following_id) VALUES (?, ?)"
                        + " ON CONFLICT DO NOTHING",
                follower,
                following);
    }

    private int insertPostLike(UUID userId, UUID postId) {
        return jdbc.update(
                "INSERT INTO post_likes (user_id, post_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
                userId,
                postId);
    }

    private int insertPostSave(UUID userId, UUID postId) {
        return jdbc.update(
                "INSERT INTO post_saves (user_id, post_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
                userId,
                postId);
    }

    private int insertCommentLike(UUID userId, UUID commentId) {
        return jdbc.update(
                "INSERT INTO comment_likes (user_id, comment_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
                userId,
                commentId);
    }

    private String handle(String name) {
        StringBuilder sb = new StringBuilder();
        for (char c : name.toLowerCase().toCharArray()) {
            if (c == ' ') {
                sb.append('.');
            } else if (c >= 'a' && c <= 'z' || c >= '0' && c <= '9') {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private String caption(SecureRandom rnd) {
        StringBuilder sb = new StringBuilder(CAPTIONS[rnd.nextInt(CAPTIONS.length)]);
        int tags = 1 + rnd.nextInt(2);
        for (int i = 0; i < tags; i++) {
            sb.append(" #").append(TAGS[rnd.nextInt(TAGS.length)]);
        }
        return sb.toString();
    }

    private String picsum(String seed, int width, int height) {
        return "https://picsum.photos/seed/" + seed + "/" + width + "/" + height + ".jpg";
    }

    private String avatar(String seed) {
        return picsum(seed, 400, 400);
    }
}
