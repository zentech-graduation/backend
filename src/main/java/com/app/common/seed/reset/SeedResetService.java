package com.app.common.seed.reset;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Wipes every seedable table before a fresh seed run.
 *
 * <p>Excluded deliberately, never truncated: {@code flyway_schema_history} (migration bookkeeping,
 * not domain data), {@code notification_type_configs}/{@code moderation_action_configs}/ {@code
 * report_reason_configs} (enum display metadata owned by Flyway, V18), {@code system_settings}
 * (operational config, not seed content), {@code feature_flags} (operational toggles, not seed
 * content). A future contributor adding a new reference/config table should add it here.
 */
@Slf4j
@Service
@Profile("dev")
@RequiredArgsConstructor
public class SeedResetService {

    private final JdbcTemplate jdbc;

    // Verified against a live `\dt` on 2026-08-25: every name below matches the running schema
    // exactly, no renames since database/schema.sql was last regenerated.
    private static final String[] TRUNCATE_ORDER = {
        // FK-leaf tables first; CASCADE covers the rest, but explicit order keeps intent readable.
        "comment_likes",
        "post_likes",
        "post_saves",
        "story_likes",
        "story_views",
        "comment_write_idempotency",
        "message_write_idempotency",
        "post_media",
        "post_hashtags",
        "post_categories",
        "post_user_tags",
        "post_edit_history",
        "post_interaction_scores",
        "comments",
        "messages",
        "conversation_participants",
        "conversations",
        "archived_group_messages",
        "archived_group_participants",
        "archived_group_conversations",
        "stories",
        "notifications",
        "reports",
        "admin_actions",
        "user_warnings",
        "user_strikes",
        "user_similarity",
        "user_interests",
        "push_tokens",
        "refresh_tokens",
        "oauth_accounts",
        "user_credentials",
        "user_settings",
        "follows",
        "blocks",
        "hashtag_trending",
        "hashtags",
        "categories",
        "posts",
        "media_assets",
        "processed_messages",
        "outbox_events",
        "platform_stats",
        "users"
    };

    /**
     * Truncates every seedable table listed in {@link #TRUNCATE_ORDER}, plus every declared {@code
     * user_events} partition, so a fresh seed run starts from an empty domain dataset.
     *
     * <p>Never truncates the config/reference tables documented in this class's Javadoc, and never
     * touches {@code flyway_schema_history}. FK checks are disabled only for the duration of the
     * wipe, since {@code TRUNCATE ... CASCADE} in dependency order would otherwise still fail on
     * tables with circular or forward references.
     *
     * <p>The entire sequence (disabling FK checks, every {@code TRUNCATE}, partition discovery, and
     * restoring FK checks) runs inside a single {@link
     * org.springframework.jdbc.core.ConnectionCallback} so every statement is provably issued on
     * the same physical connection. A pooled HikariCP connection only resets a small fixed set of
     * session properties on return to the pool - {@code session_replication_role} is not one of
     * them - so splitting this sequence across separate {@code JdbcTemplate} calls could let {@code
     * 'origin'} land on a different connection than the one that set {@code 'replica'}, leaving a
     * pooled connection permanently in trigger-disabled mode for whatever borrows it next.
     */
    public void reset() {
        jdbc.execute(
                (Connection connection) -> {
                    try (Statement statement = connection.createStatement()) {
                        // FK checks off for the wipe only, same connection start to finish.
                        statement.execute("SET session_replication_role = 'replica'");
                        try {
                            for (String table : TRUNCATE_ORDER) {
                                statement.execute("TRUNCATE TABLE " + table + " CASCADE");
                            }
                            truncateUserEventsPartitions(statement);
                        } finally {
                            statement.execute("SET session_replication_role = 'origin'");
                        }
                    }
                    return null;
                });
        log.info("[seed] reset complete: {} tables truncated", TRUNCATE_ORDER.length);
    }

    private void truncateUserEventsPartitions(Statement statement) throws SQLException {
        List<String> partitions = new ArrayList<>();
        try (ResultSet rs =
                statement.executeQuery(
                        "SELECT c.relname FROM pg_inherits i"
                                + " JOIN pg_class c ON c.oid = i.inhrelid"
                                + " JOIN pg_class p ON p.oid = i.inhparent"
                                + " WHERE p.relname = 'user_events'")) {
            while (rs.next()) {
                partitions.add(rs.getString(1));
            }
        }
        for (String partition : partitions) {
            statement.execute("TRUNCATE TABLE " + partition);
        }
        statement.execute("TRUNCATE TABLE user_events_default");
    }
}
