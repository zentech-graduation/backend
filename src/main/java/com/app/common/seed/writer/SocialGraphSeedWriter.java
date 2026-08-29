package com.app.common.seed.writer;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.app.common.seed.loader.SeedContent;
import com.app.common.seed.model.UserSeed;
import com.app.common.seed.time.SeedTimeline;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Seeds the follow graph ({@code follows}) and block list ({@code blocks}) across the 90 accounts
 * from {@code users.json}.
 *
 * <p>Neither table has a source JSON file - {@code users.json} carries only each account's {@code
 * is_private} flag, not a scripted social graph - so this writer generates the topology itself with
 * a fixed-seed {@link Random}, independent of {@link SeedTimeline}'s own random stream, so
 * re-running the writer against the same seed content reproduces the same graph.
 *
 * <p>Three QA-account behaviours documented in {@code users.json}'s {@code qa_note} fields are
 * guaranteed by construction rather than left to chance: {@code user_new_empty} is excluded from
 * both sides of every follow assignment this writer makes, so it neither receives a follow ("zero
 * followers" per its note) nor is ever picked as a follower of anyone else ("zero following" per
 * its note) - the isolated node the cold-start recommendation case needs, where both the account's
 * own following feed and its inbound audience are empty. {@code user_private} always receives a
 * minimum number of inbound pending requests ("pending follow requests inbound" per its note), and
 * {@code user_power} always receives a large explicit batch of accepted followers ("high follower
 * count" per its note).
 */
@Slf4j
@Service
@Profile("dev")
@RequiredArgsConstructor
public class SocialGraphSeedWriter {

    private static final long GRAPH_RANDOM_SEED = 8_690_251L;
    private static final int TOTAL_FOLLOWS_TARGET = 2500;
    private static final int MAX_PENDING_TOWARD_PRIVATE = 40;
    private static final int TOTAL_BLOCKS_TARGET = 25;
    private static final int MIN_FOLLOWING_PER_USER = 15;
    private static final int MAX_FOLLOWING_PER_USER = 40;

    private static final String EMPTY_SOCIAL_GRAPH_USERNAME = "user_new_empty";
    private static final String GUARANTEED_PENDING_TARGET_USERNAME = "user_private";
    private static final int GUARANTEED_PENDING_COUNT = 6;
    private static final String BOOSTED_FOLLOWER_USERNAME = "user_power";
    private static final int BOOSTED_FOLLOWER_COUNT = 55;

    private static final String INSERT_FOLLOW_SQL =
            "INSERT INTO follows (follower_id, following_id, status, created_at) VALUES (?, ?,"
                    + " ?::follow_status, ?)";
    private static final String INSERT_BLOCK_SQL =
            "INSERT INTO blocks (blocker_id, blocked_id, created_at) VALUES (?, ?, ?)";

    private final JdbcTemplate jdbc;

    /**
     * Inserts the seeded {@code follows} (~{@value #TOTAL_FOLLOWS_TARGET} rows, with up to {@value
     * #MAX_PENDING_TOWARD_PRIVATE} left {@code pending} toward private accounts) and {@code blocks}
     * (~{@value #TOTAL_BLOCKS_TARGET} pairs) rows.
     *
     * <p>Never writes {@code users.follower_count}/{@code following_count} - both are
     * trigger-maintained from {@code follows}, and a {@code pending} row correctly leaves them
     * unchanged (see {@code docs/modules/social/DATA_RULES.md}).
     *
     * @param usersByUsername username-to-id map produced by {@link UserSeedWriter#write}
     */
    public void write(
            SeedContent content, Map<String, UUID> usersByUsername, SeedTimeline timeline) {
        List<UserSeed> users = content.users();
        Map<String, Boolean> isPrivateByUsername = new HashMap<>();
        for (UserSeed user : users) {
            isPrivateByUsername.put(user.username(), user.isPrivate());
        }
        Map<UUID, Instant> createdAtByUserId = fetchCreatedAtByUserId();

        Random random = new Random(GRAPH_RANDOM_SEED);
        List<Object[]> blockRows = new ArrayList<>();
        Set<String> blockedUnorderedPairs = new HashSet<>();
        Set<String> blockedDirectedPairs = new HashSet<>();
        generateBlocks(
                users,
                usersByUsername,
                createdAtByUserId,
                timeline,
                random,
                blockRows,
                blockedUnorderedPairs,
                blockedDirectedPairs);

        List<Object[]> followRows = new ArrayList<>();
        Set<String> followPairs = new HashSet<>();
        int[] pendingCount = {0};

        addGuaranteedPendingFollows(
                users,
                usersByUsername,
                createdAtByUserId,
                timeline,
                random,
                followRows,
                followPairs,
                blockedDirectedPairs,
                pendingCount);
        addBoostedFollowers(
                users,
                usersByUsername,
                createdAtByUserId,
                timeline,
                random,
                followRows,
                followPairs,
                blockedDirectedPairs);
        fillRandomFollows(
                users,
                usersByUsername,
                isPrivateByUsername,
                createdAtByUserId,
                timeline,
                random,
                followRows,
                followPairs,
                blockedDirectedPairs,
                pendingCount);

        jdbc.batchUpdate(INSERT_FOLLOW_SQL, followRows, followRows.size(), this::bindFollowRow);
        jdbc.batchUpdate(INSERT_BLOCK_SQL, blockRows, blockRows.size(), this::bindBlockRow);

        log.info(
                "[seed] follows: {} rows written ({} pending), blocks: {} rows written",
                followRows.size(),
                pendingCount[0],
                blockRows.size());
    }

    // Read back from the DB rather than recomputing via SeedTimeline.userCreatedAt(UserSeed):
    // that method draws from SeedTimeline's shared Random stream, so calling it a second time
    // here would advance the stream and return a value different from what UserSeedWriter already
    // persisted. Querying the row UserSeedWriter wrote is the only way to get the true value.
    private Map<UUID, Instant> fetchCreatedAtByUserId() {
        Map<UUID, Instant> createdAtByUserId = new HashMap<>();
        jdbc.query(
                "SELECT id, created_at FROM users",
                rs -> {
                    UUID id = (UUID) rs.getObject("id");
                    Instant createdAt = rs.getTimestamp("created_at").toInstant();
                    createdAtByUserId.put(id, createdAt);
                });
        return createdAtByUserId;
    }

    private void generateBlocks(
            List<UserSeed> users,
            Map<String, UUID> usersByUsername,
            Map<UUID, Instant> createdAtByUserId,
            SeedTimeline timeline,
            Random random,
            List<Object[]> blockRows,
            Set<String> blockedUnorderedPairs,
            Set<String> blockedDirectedPairs) {
        int attempts = 0;
        int maxAttempts = TOTAL_BLOCKS_TARGET * 50;
        while (blockRows.size() < TOTAL_BLOCKS_TARGET && attempts < maxAttempts) {
            attempts++;
            UserSeed blocker = users.get(random.nextInt(users.size()));
            UserSeed blocked = users.get(random.nextInt(users.size()));
            if (blocker.username().equals(blocked.username())) {
                continue;
            }
            String unorderedKey = unorderedPairKey(blocker.username(), blocked.username());
            if (!blockedUnorderedPairs.add(unorderedKey)) {
                continue;
            }
            UUID blockerId = usersByUsername.get(blocker.username());
            UUID blockedId = usersByUsername.get(blocked.username());
            Instant createdAt =
                    timeline.followCreatedAt(
                            createdAtByUserId.get(blockerId), createdAtByUserId.get(blockedId));
            blockRows.add(new Object[] {blockerId, blockedId, Timestamp.from(createdAt)});
            blockedDirectedPairs.add(blocker.username() + "->" + blocked.username());
            blockedDirectedPairs.add(blocked.username() + "->" + blocker.username());
        }
    }

    // Directly satisfies user_private's qa_note ("pending follow requests inbound") without relying
    // on the general random fill below to happen to land enough pending edges on this one account.
    private void addGuaranteedPendingFollows(
            List<UserSeed> users,
            Map<String, UUID> usersByUsername,
            Map<UUID, Instant> createdAtByUserId,
            SeedTimeline timeline,
            Random random,
            List<Object[]> followRows,
            Set<String> followPairs,
            Set<String> blockedDirectedPairs,
            int[] pendingCount) {
        List<UserSeed> candidates = new ArrayList<>(users);
        Collections.shuffle(candidates, random);
        int added = 0;
        for (UserSeed follower : candidates) {
            if (added >= GUARANTEED_PENDING_COUNT) {
                break;
            }
            if (follower.username().equals(GUARANTEED_PENDING_TARGET_USERNAME)) {
                continue;
            }
            if (follower.username().equals(EMPTY_SOCIAL_GRAPH_USERNAME)) {
                continue;
            }
            if (blockedDirectedPairs.contains(
                    follower.username() + "->" + GUARANTEED_PENDING_TARGET_USERNAME)) {
                continue;
            }
            addFollow(
                    follower.username(),
                    GUARANTEED_PENDING_TARGET_USERNAME,
                    "pending",
                    usersByUsername,
                    createdAtByUserId,
                    timeline,
                    followRows,
                    followPairs);
            pendingCount[0]++;
            added++;
        }
    }

    // Directly satisfies user_power's qa_note ("high follower count") without relying on uniform
    // random sampling to happen to favor this one account.
    private void addBoostedFollowers(
            List<UserSeed> users,
            Map<String, UUID> usersByUsername,
            Map<UUID, Instant> createdAtByUserId,
            SeedTimeline timeline,
            Random random,
            List<Object[]> followRows,
            Set<String> followPairs,
            Set<String> blockedDirectedPairs) {
        List<UserSeed> candidates = new ArrayList<>(users);
        Collections.shuffle(candidates, random);
        int added = 0;
        for (UserSeed follower : candidates) {
            if (added >= BOOSTED_FOLLOWER_COUNT) {
                break;
            }
            if (follower.username().equals(BOOSTED_FOLLOWER_USERNAME)) {
                continue;
            }
            if (follower.username().equals(EMPTY_SOCIAL_GRAPH_USERNAME)) {
                continue;
            }
            if (blockedDirectedPairs.contains(
                    follower.username() + "->" + BOOSTED_FOLLOWER_USERNAME)) {
                continue;
            }
            addFollow(
                    follower.username(),
                    BOOSTED_FOLLOWER_USERNAME,
                    "accepted",
                    usersByUsername,
                    createdAtByUserId,
                    timeline,
                    followRows,
                    followPairs);
            added++;
        }
    }

    private void fillRandomFollows(
            List<UserSeed> users,
            Map<String, UUID> usersByUsername,
            Map<String, Boolean> isPrivateByUsername,
            Map<UUID, Instant> createdAtByUserId,
            SeedTimeline timeline,
            Random random,
            List<Object[]> followRows,
            Set<String> followPairs,
            Set<String> blockedDirectedPairs,
            int[] pendingCount) {
        List<UserSeed> followerOrder = new ArrayList<>(users);
        Collections.shuffle(followerOrder, random);

        for (UserSeed follower : followerOrder) {
            if (followRows.size() >= TOTAL_FOLLOWS_TARGET) {
                break;
            }
            if (follower.username().equals(EMPTY_SOCIAL_GRAPH_USERNAME)) {
                continue;
            }
            int targetFollowingCount =
                    MIN_FOLLOWING_PER_USER
                            + random.nextInt(MAX_FOLLOWING_PER_USER - MIN_FOLLOWING_PER_USER + 1);

            List<UserSeed> candidateOrder = new ArrayList<>(users);
            Collections.shuffle(candidateOrder, random);

            int addedForThisFollower = 0;
            for (UserSeed candidate : candidateOrder) {
                if (addedForThisFollower >= targetFollowingCount
                        || followRows.size() >= TOTAL_FOLLOWS_TARGET) {
                    break;
                }
                if (candidate.username().equals(follower.username())) {
                    continue;
                }
                if (candidate.username().equals(EMPTY_SOCIAL_GRAPH_USERNAME)) {
                    continue;
                }
                if (followPairs.contains(follower.username() + "->" + candidate.username())) {
                    continue;
                }
                if (blockedDirectedPairs.contains(
                        follower.username() + "->" + candidate.username())) {
                    continue;
                }
                boolean candidateIsPrivate =
                        Boolean.TRUE.equals(isPrivateByUsername.get(candidate.username()));
                if (candidateIsPrivate && pendingCount[0] >= MAX_PENDING_TOWARD_PRIVATE) {
                    continue;
                }
                String status = candidateIsPrivate ? "pending" : "accepted";
                addFollow(
                        follower.username(),
                        candidate.username(),
                        status,
                        usersByUsername,
                        createdAtByUserId,
                        timeline,
                        followRows,
                        followPairs);
                if (candidateIsPrivate) {
                    pendingCount[0]++;
                }
                addedForThisFollower++;
            }
        }
    }

    private void addFollow(
            String followerUsername,
            String followingUsername,
            String status,
            Map<String, UUID> usersByUsername,
            Map<UUID, Instant> createdAtByUserId,
            SeedTimeline timeline,
            List<Object[]> followRows,
            Set<String> followPairs) {
        String pairKey = followerUsername + "->" + followingUsername;
        if (!followPairs.add(pairKey)) {
            return;
        }
        UUID followerId = usersByUsername.get(followerUsername);
        UUID followingId = usersByUsername.get(followingUsername);
        Instant createdAt =
                timeline.followCreatedAt(
                        createdAtByUserId.get(followerId), createdAtByUserId.get(followingId));
        followRows.add(new Object[] {followerId, followingId, status, Timestamp.from(createdAt)});
    }

    private String unorderedPairKey(String usernameA, String usernameB) {
        return usernameA.compareTo(usernameB) < 0
                ? usernameA + "|" + usernameB
                : usernameB + "|" + usernameA;
    }

    private void bindFollowRow(PreparedStatement ps, Object[] row) throws SQLException {
        ps.setObject(1, row[0]);
        ps.setObject(2, row[1]);
        ps.setString(3, (String) row[2]);
        ps.setTimestamp(4, (Timestamp) row[3]);
    }

    private void bindBlockRow(PreparedStatement ps, Object[] row) throws SQLException {
        ps.setObject(1, row[0]);
        ps.setObject(2, row[1]);
        ps.setTimestamp(3, (Timestamp) row[2]);
    }
}
