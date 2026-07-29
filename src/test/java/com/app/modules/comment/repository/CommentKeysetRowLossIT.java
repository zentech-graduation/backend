package com.app.modules.comment.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestConstructor;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.app.modules.comment.entity.Comment;

/**
 * Guards against keyset row loss when approved comments or replies share a boundary {@code
 * created_at}. Pages with a size that cuts into the tie-group and asserts every row is returned
 * exactly once; a dropped row proves a strict-inequality regression, a duplicated row a non-strict
 * one.
 */
@DataJpaTest(
        properties = {
            "spring.docker.compose.enabled=false",
            "spring.datasource.hikari.data-source-properties.stringtype=unspecified"
        })
@Testcontainers
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class CommentKeysetRowLossIT {

    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final OffsetDateTime SHARED_INSTANT =
            OffsetDateTime.of(2026, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC);
    private static final int PAGE_SIZE = 2;

    private final CommentRepository commentRepository;
    private final JdbcClient jdbcClient;

    CommentKeysetRowLossIT(CommentRepository commentRepository, JdbcClient jdbcClient) {
        this.commentRepository = commentRepository;
        this.jdbcClient = jdbcClient;
    }

    @DynamicPropertySource
    static void register(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.enabled", () -> true);
    }

    @Test
    void topLevelComments_tieGroupOnCreatedAt_pagesEveryRowExactlyOnce() {
        UUID user = insertUser("commenter");
        UUID postId = insertPost(user);
        List<UUID> expected = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            expected.add(insertTopLevelComment(postId, user, SHARED_INSTANT));
        }

        List<UUID> seen = new ArrayList<>();
        List<Comment> page = commentRepository.findFirstTopLevel(postId, page());
        int guard = 0;
        while (!page.isEmpty() && guard++ < 100) {
            page.forEach(c -> seen.add(c.getId()));
            Comment last = page.get(page.size() - 1);
            page =
                    commentRepository.findTopLevelBefore(
                            postId, last.getCreatedAt(), last.getId(), page());
        }

        assertThat(seen).containsExactlyInAnyOrderElementsOf(expected);
    }

    @Test
    void replies_tieGroupOnCreatedAt_pagesEveryRowExactlyOnce() {
        UUID user = insertUser("replier");
        UUID postId = insertPost(user);
        UUID parentId = insertTopLevelComment(postId, user, SHARED_INSTANT.minusHours(1));
        List<UUID> expected = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            expected.add(insertReply(postId, user, parentId, SHARED_INSTANT));
        }

        List<UUID> seen = new ArrayList<>();
        List<Comment> page = commentRepository.findFirstReplies(parentId, page());
        int guard = 0;
        while (!page.isEmpty() && guard++ < 100) {
            page.forEach(c -> seen.add(c.getId()));
            Comment last = page.get(page.size() - 1);
            page =
                    commentRepository.findRepliesBefore(
                            parentId, last.getCreatedAt(), last.getId(), page());
        }

        assertThat(seen).containsExactlyInAnyOrderElementsOf(expected);
    }

    private static PageRequest page() {
        return PageRequest.of(0, PAGE_SIZE);
    }

    private UUID insertUser(String username) {
        return jdbcClient
                .sql(
                        """
						INSERT INTO users(username, email, display_name)
						VALUES (:username, :email, :displayName)
						RETURNING id
						""")
                .param("username", username)
                .param("email", username + "@example.com")
                .param("displayName", username)
                .query(UUID.class)
                .single();
    }

    private UUID insertPost(UUID userId) {
        return jdbcClient
                .sql(
                        "INSERT INTO posts(user_id, post_type, status)"
                                + " VALUES (:userId, 'image', 'published') RETURNING id")
                .param("userId", userId)
                .query(UUID.class)
                .single();
    }

    private UUID insertTopLevelComment(UUID postId, UUID userId, OffsetDateTime createdAt) {
        return jdbcClient
                .sql(
                        "INSERT INTO comments(post_id, user_id, depth, content, moderation_status,"
                                + " created_at) VALUES (:postId, :userId, 0, 'text', 'approved',"
                                + " :createdAt) RETURNING id")
                .param("postId", postId)
                .param("userId", userId)
                .param("createdAt", createdAt)
                .query(UUID.class)
                .single();
    }

    private UUID insertReply(UUID postId, UUID userId, UUID parentId, OffsetDateTime createdAt) {
        return jdbcClient
                .sql(
                        "INSERT INTO comments(post_id, user_id, parent_id, root_id, depth, content,"
                                + " moderation_status, created_at) VALUES (:postId, :userId, :parentId,"
                                + " :parentId, 1, 'text', 'approved', :createdAt) RETURNING id")
                .param("postId", postId)
                .param("userId", userId)
                .param("parentId", parentId)
                .param("createdAt", createdAt)
                .query(UUID.class)
                .single();
    }
}
