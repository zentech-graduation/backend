package com.app.common.devseed;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
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
 *
 * <p>In addition to the 16 regular users and the JohnDoe reviewer, this seeder creates:
 *
 * <ul>
 *   <li>An {@code admin} account (role=admin) and a {@code moderator} account (role=moderator).
 *   <li>Stories for the first 5 seeded users, each with views and likes.
 *   <li>Four 1-1 conversations between JohnDoe and four seeded users.
 *   <li>Admin actions: two warns, one suspension, one post removal, and two reports (one resolved).
 * </ul>
 */
@Service
@Profile("dev")
@RequiredArgsConstructor
public class DevDataSeedServiceImpl implements DevDataSeedService {

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;

    private static final String PASSWORD = "Password123!";
    private static final String DOMAIN = "luvax.test";
    private static final String REVIEWER_USERNAME = "JohnDoe";
    private static final String REVIEWER_EMAIL = "johndoe@luvax.test";
    private static final String REVIEWER_NAME = "John Doe";
    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_EMAIL = "admin@luvax.test";
    private static final String MOD_USERNAME = "moderator";
    private static final String MOD_EMAIL = "moderator@luvax.test";
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

    private static final String[] STORY_CAPTIONS = {
        "today",
        "morning light",
        "out here",
        "golden hour",
        "just this",
        "quiet day",
        "on the road",
        "catching it",
    };

    // Alternating lines of dialogue between JohnDoe (J) and the other user (U).
    // Each conversation uses a slice of this array starting at a different offset.
    private static final String[][] CONVERSATION_SCRIPTS = {
        {
            "J: hey, love your last post",
            "U: thank you! took me a while to get that shot",
            "J: where was that exactly?",
            "U: somewhere up north, near the coast",
            "J: going to try and make it there this summer",
            "U: you should, worth every hour of the drive",
        },
        {
            "J: saw your story this morning",
            "U: haha yeah that was early",
            "J: 6am light is something else",
            "U: once you start you can't stop",
            "J: fair warning noted",
            "U: bring coffee",
            "J: always",
        },
        {
            "J: can I ask what film stock you shoot on?",
            "U: kodak gold 200 mostly",
            "J: I've been on portra 400 but thinking of switching",
            "U: gold has a warmer grain, great for outdoor",
            "J: good to know, cheers",
        },
        {
            "J: that series you did last month was incredible",
            "U: means a lot coming from you",
            "J: how long did you spend on location?",
            "U: three days, mostly waiting for light",
            "J: patience is the real skill",
            "U: exactly",
            "J: when is the next one?",
            "U: probably spring, weather dependent",
        },
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
        Random rnd = new Random(20260817L);

        // --- Seed regular users ---
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
                    hash,
                    "user");
            userIds.add(id);
        }

        // --- Seed reviewer (JohnDoe) ---
        UUID reviewerId = UUID.randomUUID();
        insertUser(
                reviewerId,
                REVIEWER_USERNAME,
                REVIEWER_EMAIL,
                REVIEWER_NAME,
                "here to see everything",
                avatar("reviewer"),
                null,
                hash,
                "user");

        // --- Seed admin account ---
        UUID adminId = UUID.randomUUID();
        insertUser(
                adminId,
                ADMIN_USERNAME,
                ADMIN_EMAIL,
                "Platform Admin",
                "keeping the community safe",
                avatar("admin"),
                null,
                hash,
                "admin");

        // --- Seed moderator account ---
        UUID modId = UUID.randomUUID();
        insertUser(
                modId,
                MOD_USERNAME,
                MOD_EMAIL,
                "Moderator",
                "reviewing content for community standards",
                avatar("mod"),
                null,
                hash,
                "moderator");

        // --- Seed posts ---
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

        // --- Follows: random cross-follows + reviewer follows all ---
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

        // --- Post engagement ---
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

        // --- Stories: first 5 users get 2 stories each ---
        int storyCount = 0;
        int storyViews = 0;
        int storyLikes = 0;
        int storyMediaCount = 0;
        for (int u = 0; u < 5 && u < userIds.size(); u++) {
            UUID owner = userIds.get(u);
            for (int s = 0; s < 2; s++) {
                int[] shape = IMG_SHAPES[rnd.nextInt(IMG_SHAPES.length)];
                UUID mediaId = insertImage(owner, "story-" + u + "-" + s, shape);
                storyMediaCount++;
                String caption = STORY_CAPTIONS[(u * 2 + s) % STORY_CAPTIONS.length];
                UUID storyId = insertStory(owner, mediaId, caption);
                storyCount++;

                // JohnDoe always views
                storyViews += insertStoryView(storyId, reviewerId);

                // 3 more random viewers
                for (int v = 0; v < 3; v++) {
                    UUID viewer = userIds.get(rnd.nextInt(userIds.size()));
                    if (!viewer.equals(owner)) {
                        storyViews += insertStoryView(storyId, viewer);
                    }
                }

                // 2-3 random users like
                int likeCount = 2 + rnd.nextInt(2);
                for (int l = 0; l < likeCount; l++) {
                    UUID liker = userIds.get(rnd.nextInt(userIds.size()));
                    if (!liker.equals(owner)) {
                        storyLikes += insertStoryLike(storyId, liker);
                    }
                }
            }
        }

        // --- Messages: JohnDoe with 4 seeded users ---
        int conversationCount = 0;
        int messageCount = 0;
        for (int i = 0; i < 4 && i < userIds.size(); i++) {
            UUID otherId = userIds.get(i);
            String pairKey = pairKey(reviewerId, otherId);
            UUID convId = insertConversation(reviewerId, pairKey);
            insertConversationParticipant(convId, reviewerId);
            insertConversationParticipant(convId, otherId);
            conversationCount++;

            String[] script = CONVERSATION_SCRIPTS[i];
            UUID lastMsgAt = null;
            for (String line : script) {
                boolean isJohn = line.startsWith("J:");
                UUID sender = isJohn ? reviewerId : otherId;
                String content = line.substring(3).trim();
                insertMessage(convId, sender, content);
                messageCount++;
            }
            // Update last_message_at on the conversation
            jdbc.update("UPDATE conversations SET last_message_at = NOW() WHERE id = ?", convId);
        }

        // --- Admin data ---
        // Pick stable users for discipline targets: index 5 (warn+warn) and index 6 (warn+suspend)
        UUID warnTargetA = userIds.get(5); // Jae Okoro - gets 1 warning
        UUID warnTargetB = userIds.get(6); // Ren Kato  - gets 1 warning then suspended
        UUID removePostOwner = userIds.get(7); // Ava Lindqvist - has a post removed
        UUID reporter1 = userIds.get(8); // Tomas Feld
        UUID reporter2 = userIds.get(9); // Priya Nair

        // Find a published post owned by removePostOwner (use the first one in postIds)
        UUID postToRemove = null;
        for (int i = 0; i < postOwners.size(); i++) {
            if (postOwners.get(i).equals(removePostOwner)) {
                postToRemove = postIds.get(i);
                break;
            }
        }

        // Find a post owned by warnTargetA to be the subject of report1
        UUID reportedPost1 = null;
        for (int i = 0; i < postOwners.size(); i++) {
            if (postOwners.get(i).equals(warnTargetA)) {
                reportedPost1 = postIds.get(i);
                break;
            }
        }

        // Find a post owned by warnTargetB to be the subject of report2
        UUID reportedPost2 = null;
        for (int i = 0; i < postOwners.size(); i++) {
            if (postOwners.get(i).equals(warnTargetB)) {
                reportedPost2 = postIds.get(i);
                break;
            }
        }

        int adminActionCount = 0;
        int reportCount = 0;

        // Report 1: reporter1 reports warnTargetA's post for spam (will be resolved)
        UUID report1Id = null;
        if (reportedPost1 != null) {
            report1Id = insertReport(reporter1, "post", "spam", reportedPost1, null);
            reportCount++;
        }

        // Report 2: reporter2 reports warnTargetB's post for harassment (stays pending)
        if (reportedPost2 != null) {
            insertReport(reporter2, "post", "harassment", reportedPost2, null);
            reportCount++;
        }

        // Moderator warns warnTargetA (reason: spam)
        UUID warnActionA =
                insertAdminAction(
                        modId,
                        "warn_user",
                        warnTargetA,
                        null,
                        null,
                        null,
                        "Repeated spam comments on community posts.");
        insertUserWarning(
                warnTargetA,
                modId,
                "spam",
                "Repeated spam comments on community posts.",
                warnActionA);
        adminActionCount++;

        // Moderator warns warnTargetB (reason: harassment)
        UUID warnActionB =
                insertAdminAction(
                        modId,
                        "warn_user",
                        warnTargetB,
                        null,
                        null,
                        null,
                        "Harassing replies targeting another user.");
        insertUserWarning(
                warnTargetB,
                modId,
                "harassment",
                "Harassing replies targeting another user.",
                warnActionB);
        adminActionCount++;

        // Admin suspends warnTargetB (72-hour suspension)
        UUID suspendAction =
                insertAdminAction(
                        adminId,
                        "suspend_user",
                        warnTargetB,
                        null,
                        null,
                        null,
                        "Escalated after warning: continued harassment.");
        jdbc.update(
                "UPDATE users SET status = 'suspended',"
                        + " suspended_until = NOW() + INTERVAL '72 hours'"
                        + " WHERE id = ?",
                warnTargetB);
        adminActionCount++;

        // Admin removes post
        if (postToRemove != null) {
            UUID removeAction =
                    insertAdminAction(
                            adminId,
                            "remove_post",
                            removePostOwner,
                            "post",
                            postToRemove,
                            null,
                            "Post violates community guidelines: graphic content.");
            jdbc.update("UPDATE posts SET status = 'removed' WHERE id = ?", postToRemove);
            adminActionCount++;
        }

        // Moderator resolves report1
        if (report1Id != null) {
            UUID resolveAction =
                    insertAdminAction(
                            modId,
                            "resolve_report",
                            warnTargetA,
                            "post",
                            reportedPost1,
                            report1Id,
                            "Reviewed: user was warned, content has been addressed.");
            jdbc.update(
                    "UPDATE reports SET status = 'resolved',"
                            + " reviewed_by = ?, reviewed_at = NOW(),"
                            + " resolution_note = 'User warned; action taken.'"
                            + " WHERE id = ?",
                    modId,
                    report1Id);
            adminActionCount++;
        }

        // --- Build summary ---
        String summary =
                String.format(
                        "%d users + reviewer + admin + moderator,"
                                + " %d posts, %d media (incl. %d story media),"
                                + " %d follows, %d post-likes, %d saves,"
                                + " %d comments, %d replies, %d comment-likes,"
                                + " %d stories, %d story-views, %d story-likes,"
                                + " %d conversations, %d messages,"
                                + " %d reports, %d admin-actions",
                        userIds.size(),
                        postCount,
                        mediaCount + storyMediaCount,
                        storyMediaCount,
                        follows + reviewerFollows,
                        postLikes,
                        saves,
                        comments,
                        replies,
                        commentLikes,
                        storyCount,
                        storyViews,
                        storyLikes,
                        conversationCount,
                        messageCount,
                        reportCount,
                        adminActionCount);
        return new DevSeedResult(
                false, summary, REVIEWER_USERNAME, REVIEWER_EMAIL, PASSWORD, reviewerFollows);
    }

    // -------------------------------------------------------------------------
    // Insert helpers
    // -------------------------------------------------------------------------

    private void insertUser(
            UUID id,
            String username,
            String email,
            String name,
            String bio,
            String avatarUrl,
            String bannerUrl,
            String hash,
            String role) {
        jdbc.update(
                "INSERT INTO users (id, username, email, display_name, bio, avatar_url, banner_url,"
                        + " is_verified, status, role) VALUES (?, ?, ?, ?, ?, ?, ?, true, 'active',"
                        + " ?::user_role)",
                id,
                username,
                email,
                name,
                bio,
                avatarUrl,
                bannerUrl,
                role);
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

    private UUID insertVideo(UUID userId, Random rnd) {
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
                "INSERT INTO media_assets (id, user_id, storage_key, cdn_url, media_type,"
                        + " mime_type, file_size, width, height, duration, blurhash)"
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
                "INSERT INTO comment_likes (user_id, comment_id) VALUES (?, ?)"
                        + " ON CONFLICT DO NOTHING",
                userId,
                commentId);
    }

    // --- Story helpers ---

    private UUID insertStory(UUID userId, UUID mediaAssetId, String caption) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO stories (id, user_id, media_asset_id, story_type, caption,"
                        + " expires_at) VALUES (?, ?, ?, 'image', ?,"
                        + " NOW() + INTERVAL '48 hours')",
                id,
                userId,
                mediaAssetId,
                caption);
        return id;
    }

    private int insertStoryView(UUID storyId, UUID viewerId) {
        return jdbc.update(
                "INSERT INTO story_views (story_id, viewer_id) VALUES (?, ?)"
                        + " ON CONFLICT DO NOTHING",
                storyId,
                viewerId);
    }

    private int insertStoryLike(UUID storyId, UUID userId) {
        return jdbc.update(
                "INSERT INTO story_likes (user_id, story_id) VALUES (?, ?)"
                        + " ON CONFLICT DO NOTHING",
                userId,
                storyId);
    }

    // --- Message helpers ---

    private UUID insertConversation(UUID createdBy, String pairKey) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO conversations (id, direct_pair_key, created_by) VALUES (?, ?, ?)",
                id,
                pairKey,
                createdBy);
        return id;
    }

    private void insertConversationParticipant(UUID conversationId, UUID userId) {
        jdbc.update(
                "INSERT INTO conversation_participants (conversation_id, user_id) VALUES (?, ?)",
                conversationId,
                userId);
    }

    private void insertMessage(UUID conversationId, UUID senderId, String content) {
        jdbc.update(
                "INSERT INTO messages (id, conversation_id, sender_id, message_type, content)"
                        + " VALUES (?, ?, ?, 'text', ?)",
                UUID.randomUUID(),
                conversationId,
                senderId,
                content);
    }

    /** Builds the direct_pair_key identical to ConversationRepository's LEAST/GREATEST pattern. */
    private String pairKey(UUID a, UUID b) {
        String sa = a.toString();
        String sb = b.toString();
        return (sa.compareTo(sb) <= 0) ? sa + ":" + sb : sb + ":" + sa;
    }

    // --- Admin helpers ---

    private UUID insertReport(
            UUID reporterId,
            String reportType,
            String reportReason,
            UUID entityId,
            String description) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO reports (id, reporter_id, report_type, report_reason, entity_id,"
                        + " description, status) VALUES (?, ?, ?::report_type, ?::report_reason,"
                        + " ?, ?, 'pending')",
                id,
                reporterId,
                reportType,
                reportReason,
                entityId,
                description);
        return id;
    }

    private UUID insertAdminAction(
            UUID adminId,
            String actionType,
            UUID targetUserId,
            String targetEntityType,
            UUID targetEntityId,
            UUID reportId,
            String reason) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO admin_actions (id, admin_id, action_type, target_user_id,"
                        + " target_entity_type, target_entity_id, report_id, reason)"
                        + " VALUES (?, ?, ?::admin_action_type, ?, ?, ?, ?, ?)",
                id,
                adminId,
                actionType,
                targetUserId,
                targetEntityType,
                targetEntityId,
                reportId,
                reason);
        return id;
    }

    private void insertUserWarning(
            UUID userId, UUID issuedBy, String reasonKey, String note, UUID adminActionId) {
        jdbc.update(
                "INSERT INTO user_warnings (id, user_id, issued_by, reason_key, note,"
                        + " admin_action_id) VALUES (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(),
                userId,
                issuedBy,
                reasonKey,
                note,
                adminActionId);
    }

    // -------------------------------------------------------------------------
    // String helpers
    // -------------------------------------------------------------------------

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

    private String caption(Random rnd) {
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
