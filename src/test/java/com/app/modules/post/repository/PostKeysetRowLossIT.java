package com.app.modules.post.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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

import com.app.modules.post.entity.Post;
import com.app.modules.post.entity.PostLike;

/**
 * Reproduces and guards against keyset row loss when multiple posts share a boundary {@code
 * created_at}. A cursor over {@code created_at} alone excludes an entire tie-group once a page
 * boundary falls inside it; the fix orders and compares on the {@code (created_at, id)} tuple.
 */
@DataJpaTest(
        properties = {
            "spring.docker.compose.enabled=false",
            "spring.datasource.hikari.data-source-properties.stringtype=unspecified"
        })
@Testcontainers
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
class PostKeysetRowLossIT {

    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private static final OffsetDateTime SHARED_INSTANT =
            OffsetDateTime.of(2026, 1, 1, 12, 0, 0, 0, ZoneOffset.UTC);

    private final PostRepository postRepository;
    private final PostLikeRepository postLikeRepository;
    private final JdbcClient jdbcClient;

    PostKeysetRowLossIT(
            PostRepository postRepository,
            PostLikeRepository postLikeRepository,
            JdbcClient jdbcClient) {
        this.postRepository = postRepository;
        this.postLikeRepository = postLikeRepository;
        this.jdbcClient = jdbcClient;
    }

    @DynamicPropertySource
    static void register(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.enabled", () -> true);
    }

    @Test
    void userPosts_tieGroupOnCreatedAt_pagesEveryRowExactlyOnce() {
        UUID author = insertUser("tie_author");
        // Five posts sharing one created_at instant form a tie-group larger than the page size, so
        // a
        // page boundary is forced to fall inside it.
        for (int i = 0; i < 5; i++) {
            insertPost(author, SHARED_INSTANT);
        }

        Set<UUID> seen = pageAllUserPosts(author, 2);

        assertThat(seen).hasSize(5);
    }

    @Test
    void likes_tieGroupOnCreatedAt_pagesEveryRowExactlyOnce() {
        UUID postOwner = insertUser("like_post_owner");
        UUID postId = insertPostReturningId(postOwner);
        // Five likers at one shared created_at instant; the composite-key tiebreaker is user_id.
        for (int i = 0; i < 5; i++) {
            insertLike(postId, insertUser("liker_" + i), SHARED_INSTANT);
        }

        Set<UUID> seen = new LinkedHashSet<>();
        List<PostLike> page = postLikeRepository.findFirstLikers(postId, PageRequest.of(0, 2));
        int guard = 0;
        while (!page.isEmpty() && guard++ < 100) {
            page.forEach(l -> seen.add(l.getId().getUserId()));
            PostLike last = page.get(page.size() - 1);
            page =
                    postLikeRepository.findLikersBefore(
                            postId,
                            last.getCreatedAt(),
                            last.getId().getUserId(),
                            PageRequest.of(0, 2));
        }

        assertThat(seen).hasSize(5);
    }

    private Set<UUID> pageAllUserPosts(UUID author, int pageSize) {
        Set<UUID> seen = new LinkedHashSet<>();
        List<Post> page = postRepository.findFirstUserPosts(author, PageRequest.of(0, pageSize));
        int guard = 0;
        while (!page.isEmpty() && guard++ < 100) {
            page.forEach(p -> seen.add(p.getId()));
            Post last = page.get(page.size() - 1);
            page =
                    postRepository.findUserPostsBefore(
                            author, last.getCreatedAt(), last.getId(), PageRequest.of(0, pageSize));
        }
        return seen;
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

    private UUID insertPostReturningId(UUID userId) {
        return jdbcClient
                .sql(
                        "INSERT INTO posts(user_id, post_type, status)"
                                + " VALUES (:userId, 'image', 'published') RETURNING id")
                .param("userId", userId)
                .query(UUID.class)
                .single();
    }

    private void insertLike(UUID postId, UUID userId, OffsetDateTime createdAt) {
        jdbcClient
                .sql(
                        "INSERT INTO post_likes(post_id, user_id, created_at)"
                                + " VALUES (:postId, :userId, :createdAt)")
                .param("postId", postId)
                .param("userId", userId)
                .param("createdAt", createdAt)
                .update();
    }

    private void insertPost(UUID userId, OffsetDateTime createdAt) {
        jdbcClient
                .sql(
                        """
						INSERT INTO posts(user_id, post_type, status, created_at)
						VALUES (:userId, 'image', 'published', :createdAt)
						""")
                .param("userId", userId)
                .param("createdAt", createdAt)
                .update();
    }
}
