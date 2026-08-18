package com.app.modules.admin.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.app.common.security.jwt.JwtTokenProvider;
import com.app.modules.mail.service.MailService;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.profiles.active=dev",
            "spring.docker.compose.enabled=false",
            "spring.autoconfigure.exclude="
                    + "org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration"
        })
@Testcontainers
@AutoConfigureTestRestTemplate
class AdminControllerIT {

    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    @DynamicPropertySource
    static void register(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> "");
        registry.add("JWT_SECRET", () -> "admin-controller-it-secret-32-chars-minimum!!!");
        registry.add("JWT_ISSUER", () -> "https://admin.it.local");
        registry.add("JWT_AUDIENCE", () -> "App");
        registry.add("ACCESS_TOKEN_TTL", () -> 900L);
        registry.add("REFRESH_TOKEN_TTL", () -> 3600L);
        registry.add("APP_BASE_URL", () -> "http://localhost:8080");
        registry.add("CORS_ALLOWED_ORIGINS", () -> "http://localhost:3000");
        registry.add("RESEND_API_KEY", () -> "re_test_dummy_key");
        registry.add("MAIL_FROM_ADDRESS", () -> "noreply@test.local");
        registry.add("MAIL_FROM_NAME", () -> "App IT");
        registry.add("MAIL_APP_NAME", () -> "App");
        registry.add("FRONTEND_BASE_URL", () -> "http://localhost:3000");
        registry.add("GOOGLE_CLIENT_ID", () -> "test-client-id");
        registry.add("GOOGLE_CLIENT_SECRET", () -> "test-client-secret");
        registry.add(
                "spring.datasource.hikari.data-source-properties.stringtype", () -> "unspecified");
        registry.add("app.outbox.publisher.enabled", () -> false);
        registry.add("app.hashtag.seed.enabled", () -> false);
        registry.add("app.post.seed.enabled", () -> false);
    }

    @MockitoBean private MailService mailService;

    @Autowired private TestRestTemplate rest;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    private record TestUser(UUID id, String token) {}

    @AfterEach
    void cleanup() {
        jdbcTemplate.execute(
                "DROP TRIGGER IF EXISTS trg_fail_admin_action_insert ON admin_actions");
        jdbcTemplate.execute("DROP FUNCTION IF EXISTS fail_admin_action_insert()");
        jdbcTemplate.update("DELETE FROM admin_actions");
        jdbcTemplate.update("DELETE FROM reports");
        jdbcTemplate.update("DELETE FROM comments");
        jdbcTemplate.update("DELETE FROM posts");
        jdbcTemplate.update("DELETE FROM users");
    }

    @Test
    void banUser_clientSuppliedMetadata_isRejected() {
        TestUser actor = createUser("meta_bound_admin", "admin");
        TestUser target = createUser("meta_bound_target", "user");

        Map<String, Object> body = new java.util.HashMap<>();
        body.put("reason", "Severe abuse");
        body.put("metadata", Map.of("severity", "high"));

        ResponseEntity<Map> response =
                patch("/api/v1/admin/users/" + target.id() + "/ban", body, actor);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code")).isEqualTo("MALFORMED_REQUEST_BODY");
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM admin_actions WHERE target_user_id = ?",
                                Integer.class,
                                target.id()))
                .isZero();
    }

    @Test
    void banUser_writeResponse_carriesCreatedAt() {
        TestUser actor = createUser("created_at_admin", "admin");
        TestUser target = createUser("created_at_target", "user");

        ResponseEntity<Map> response =
                patch(
                        "/api/v1/admin/users/" + target.id() + "/ban",
                        Map.of("reason", "Audit timestamp must reach the client"),
                        actor);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(dataOf(response).get("createdAt")).isNotNull();
    }

    @Test
    void banUser_regularUser_returnsForbidden() {
        TestUser actor = createUser("forbidden_actor", "user");
        TestUser target = createUser("forbidden_target", "user");

        ResponseEntity<Map> response =
                patch(
                        "/api/v1/admin/users/" + target.id() + "/ban",
                        Map.of("reason", "Violation"),
                        actor);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void userStatusOperations_moderator_returnForbiddenAndLeaveNoTrace() {
        TestUser actor = createUser("status_moderator", "moderator");
        TestUser target = createUser("status_target", "user");

        assertThat(
                        patch(
                                        "/api/v1/admin/users/" + target.id() + "/suspend",
                                        Map.of("reason", "Repeated harassment"),
                                        actor)
                                .getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(
                        patch(
                                        "/api/v1/admin/users/" + target.id() + "/unsuspend",
                                        Map.of("reason", "Suspension completed"),
                                        actor)
                                .getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(
                        patch(
                                        "/api/v1/admin/users/" + target.id() + "/ban",
                                        Map.of("reason", "Severe abuse"),
                                        actor)
                                .getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(
                        patch(
                                        "/api/v1/admin/users/" + target.id() + "/unban",
                                        Map.of("reason", "Appeal accepted"),
                                        actor)
                                .getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT status::text FROM users WHERE id = ?",
                                String.class,
                                target.id()))
                .isEqualTo("active");
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM admin_actions WHERE admin_id = ?",
                                Integer.class,
                                actor.id()))
                .isZero();
    }

    @Test
    void userStatusOperations_administrator_applyAllFourContracts() {
        TestUser actor = createUser("status_admin", "admin");
        TestUser target = createUser("status_target", "user");

        assertThat(
                        patch(
                                        "/api/v1/admin/users/" + target.id() + "/suspend",
                                        Map.of("reason", "Repeated harassment"),
                                        actor)
                                .getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(
                        patch(
                                        "/api/v1/admin/users/" + target.id() + "/unsuspend",
                                        Map.of("reason", "Suspension completed"),
                                        actor)
                                .getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(
                        patch(
                                        "/api/v1/admin/users/" + target.id() + "/ban",
                                        Map.of("reason", "Severe abuse"),
                                        actor)
                                .getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(
                        patch(
                                        "/api/v1/admin/users/" + target.id() + "/unban",
                                        Map.of("reason", "Appeal accepted"),
                                        actor)
                                .getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT status::text FROM users WHERE id = ?",
                                String.class,
                                target.id()))
                .isEqualTo("active");
        assertThat(auditCount("suspend_user", target.id())).isEqualTo(1);
        assertThat(auditCount("unsuspend_user", target.id())).isEqualTo(1);
        assertThat(auditCount("ban_user", target.id())).isEqualTo(1);
        assertThat(auditCount("unban_user", target.id())).isEqualTo(1);
    }

    @Test
    void removePost_moderator_staysReachableOnTheSharedPrefix() {
        TestUser actor = createUser("shared_prefix_mod", "moderator");
        TestUser owner = createUser("shared_prefix_owner", "user");
        UUID postId = insertPost(owner.id());

        ResponseEntity<Map> response =
                patch(
                        "/api/v1/admin/posts/" + postId + "/remove",
                        Map.of("reason", "Policy violation"),
                        actor);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT status::text FROM posts WHERE id = ?",
                                String.class,
                                postId))
                .isEqualTo("removed");
    }

    @Test
    void suspendUser_administratorTargetingItself_returnsConflict() {
        TestUser actor = createUser("self_action_admin", "admin");

        ResponseEntity<Map> response =
                patch(
                        "/api/v1/admin/users/" + actor.id() + "/suspend",
                        Map.of("reason", "Self action"),
                        actor);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("code")).isEqualTo("ADMIN_SELF_ACTION_NOT_ALLOWED");
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT status::text FROM users WHERE id = ?",
                                String.class,
                                actor.id()))
                .isEqualTo("active");
    }

    @Test
    void banUser_administratorTargetingAnotherAdministrator_returnsForbidden() {
        TestUser actor = createUser("protected_actor", "admin");
        TestUser target = createUser("protected_admin", "admin");

        ResponseEntity<Map> response =
                patch(
                        "/api/v1/admin/users/" + target.id() + "/ban",
                        Map.of("reason", "Admin on admin"),
                        actor);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().get("code")).isEqualTo("ADMIN_TARGET_PROTECTED");
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT status::text FROM users WHERE id = ?",
                                String.class,
                                target.id()))
                .isEqualTo("active");
    }

    // The path matcher rejects this before the self-action guard is reached, so the body carries
    // the
    // generic FORBIDDEN code rather than ADMIN_SELF_ACTION_NOT_ALLOWED.
    @Test
    void suspendUser_moderatorTargetingItself_returnsForbidden() {
        TestUser actor = createUser("self_action_moderator", "moderator");

        ResponseEntity<Map> response =
                patch(
                        "/api/v1/admin/users/" + actor.id() + "/suspend",
                        Map.of("reason", "Self action"),
                        actor);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().get("code")).isEqualTo("FORBIDDEN");
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT status::text FROM users WHERE id = ?",
                                String.class,
                                actor.id()))
                .isEqualTo("active");
    }

    @Test
    void actionsForUser_oldPathUnderTheAdminOnlyPrefix_isNotReachableByAModerator() {
        TestUser actor = createUser("legacy_path_moderator", "moderator");
        TestUser target = createUser("legacy_path_target", "user");

        ResponseEntity<Map> response =
                get("/api/v1/admin/users/" + target.id() + "/actions", actor);

        assertThat(response.getStatusCode()).isIn(HttpStatus.FORBIDDEN, HttpStatus.NOT_FOUND);
    }

    @Test
    void moderatePost_removeThenRestore_updatesPostAndCreatesAuditTrail() {
        TestUser actor = createUser("post_admin", "admin");
        TestUser owner = createUser("post_owner", "user");
        UUID postId = insertPost(owner.id());

        ResponseEntity<Map> removed =
                patch(
                        "/api/v1/admin/posts/" + postId + "/remove",
                        Map.of("reason", "Policy violation"),
                        actor);
        ResponseEntity<Map> restored =
                patch(
                        "/api/v1/admin/posts/" + postId + "/restore",
                        Map.of("reason", "Appeal accepted"),
                        actor);

        assertThat(removed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restored.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> state =
                jdbcTemplate.queryForMap(
                        "SELECT status::text AS status, deleted_at FROM posts WHERE id = ?",
                        postId);
        assertThat(state.get("status")).isEqualTo("published");
        assertThat(state.get("deleted_at")).isNull();
        assertThat(auditCount("remove_post", owner.id())).isEqualTo(1);
        assertThat(auditCount("restore_post", owner.id())).isEqualTo(1);
    }

    @Test
    void moderateComment_removeThenRestore_updatesCommentAndCreatesAuditTrail() {
        TestUser actor = createUser("comment_moderator", "moderator");
        TestUser owner = createUser("comment_owner", "user");
        UUID postId = insertPost(actor.id());
        UUID commentId = insertComment(postId, owner.id());

        assertThat(
                        patch(
                                        "/api/v1/admin/comments/" + commentId + "/remove",
                                        Map.of("reason", "Harassment"),
                                        actor)
                                .getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(
                        patch(
                                        "/api/v1/admin/comments/" + commentId + "/restore",
                                        Map.of("reason", "Appeal accepted"),
                                        actor)
                                .getStatusCode())
                .isEqualTo(HttpStatus.OK);

        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT deleted_at IS NULL FROM comments WHERE id = ?",
                                Boolean.class,
                                commentId))
                .isTrue();
        assertThat(auditCount("remove_comment", owner.id())).isEqualTo(1);
        assertThat(auditCount("restore_comment", owner.id())).isEqualTo(1);
    }

    @Test
    void reportOperations_admin_resolveAndDismissReports() {
        TestUser actor = createUser("report_admin", "admin");
        TestUser reporter = createUser("report_reporter", "user");
        TestUser target = createUser("report_target", "user");
        TestUser secondTarget = createUser("report_target_2", "user");
        UUID resolvedReportId = insertReport(reporter.id(), target.id());
        UUID dismissedReportId = insertReport(reporter.id(), secondTarget.id());

        ResponseEntity<Map> resolved =
                patch(
                        "/api/v1/admin/reports/" + resolvedReportId + "/resolve",
                        Map.of("reason", "Violation confirmed"),
                        actor);
        ResponseEntity<Map> dismissed =
                patch(
                        "/api/v1/admin/reports/" + dismissedReportId + "/dismiss",
                        Map.of("reason", "No violation found"),
                        actor);

        assertThat(resolved.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(dismissed.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<String, Object> report =
                jdbcTemplate.queryForMap(
                        "SELECT status::text AS status, reviewed_by, resolution_note "
                                + "FROM reports WHERE id = ?",
                        dismissedReportId);
        assertThat(report.get("status")).isEqualTo("dismissed");
        assertThat(report.get("reviewed_by")).isEqualTo(actor.id());
        assertThat(report.get("resolution_note")).isEqualTo("No violation found");
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM admin_actions WHERE report_id = ?",
                                Integer.class,
                                dismissedReportId))
                .isEqualTo(1);
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT status::text FROM reports WHERE id = ?",
                                String.class,
                                resolvedReportId))
                .isEqualTo("resolved");
    }

    @Test
    void listAndGetActions_moderator_returnsPersistedAuditEvent() {
        TestUser author = createUser("query_admin", "admin");
        TestUser actor = createUser("query_moderator", "moderator");
        TestUser target = createUser("query_target", "user");
        ResponseEntity<Map> mutation =
                patch(
                        "/api/v1/admin/users/" + target.id() + "/ban",
                        Map.of("reason", "Severe abuse"),
                        author);
        UUID actionId = UUID.fromString((String) dataOf(mutation).get("id"));

        ResponseEntity<Map> list = get("/api/v1/admin/actions?actionType=ban_user", actor);
        ResponseEntity<Map> detail = get("/api/v1/admin/actions/" + actionId, actor);
        ResponseEntity<Map> forUser = get("/api/v1/admin/actions/for-user/" + target.id(), actor);

        assertThat(list.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(contentOf(list)).hasSize(1);
        assertThat(detail.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(dataOf(detail).get("id")).isEqualTo(actionId.toString());
        assertThat(forUser.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(contentOf(forUser)).hasSize(1);
    }

    @Test
    void listActions_sizeAboveMax_returnsBadRequest() {
        TestUser actor = createUser("size_moderator", "moderator");

        ResponseEntity<Map> response = get("/api/v1/admin/actions?limit=999", actor);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void deleteActor_existingAuditEvent_preservesAuditWithNullActor() {
        TestUser actor = createUser("cascade_admin", "admin");
        TestUser target = createUser("cascade_target", "user");
        ResponseEntity<Map> mutation =
                patch(
                        "/api/v1/admin/users/" + target.id() + "/ban",
                        Map.of("reason", "Permanent violation"),
                        actor);
        UUID actionId = UUID.fromString((String) dataOf(mutation).get("id"));

        jdbcTemplate.update("DELETE FROM users WHERE id = ?", actor.id());

        Map<String, Object> audit =
                jdbcTemplate.queryForMap(
                        "SELECT id, admin_id FROM admin_actions WHERE id = ?", actionId);
        assertThat(audit.get("id")).isEqualTo(actionId);
        assertThat(audit.get("admin_id")).isNull();
    }

    @Test
    void banUser_auditInsertFails_rollsBackUserStatus() {
        TestUser actor = createUser("rollback_admin", "admin");
        TestUser target = createUser("rollback_target", "user");
        jdbcTemplate.execute(
                "CREATE FUNCTION fail_admin_action_insert() RETURNS trigger AS $$ "
                        + "BEGIN RAISE EXCEPTION 'forced audit failure'; END; $$ LANGUAGE plpgsql");
        jdbcTemplate.execute(
                "CREATE TRIGGER trg_fail_admin_action_insert BEFORE INSERT ON admin_actions "
                        + "FOR EACH ROW EXECUTE FUNCTION fail_admin_action_insert()");

        ResponseEntity<Map> response =
                patch(
                        "/api/v1/admin/users/" + target.id() + "/ban",
                        Map.of("reason", "Rollback verification"),
                        actor);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT status::text FROM users WHERE id = ?",
                                String.class,
                                target.id()))
                .isEqualTo("active");
        assertThat(
                        jdbcTemplate.queryForObject(
                                "SELECT COUNT(*) FROM admin_actions WHERE target_user_id = ?",
                                Integer.class,
                                target.id()))
                .isZero();
    }

    private TestUser createUser(String prefix, String role) {
        UUID id = UUID.randomUUID();
        String username = prefix + "_" + id.toString().substring(0, 8);
        String email = username + "@test.local";
        jdbcTemplate.update(
                "INSERT INTO users (id, username, email, role, status, is_private, is_verified) "
                        + "VALUES (?, ?, ?, CAST(? AS user_role), 'active', FALSE, TRUE)",
                id,
                username,
                email,
                role);
        return new TestUser(id, jwtTokenProvider.generateAccessToken(id, role.toUpperCase()));
    }

    private UUID insertPost(UUID ownerId) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO posts (id, user_id, caption, post_type, status) "
                        + "VALUES (?, ?, 'Moderation target', 'text', 'published')",
                id,
                ownerId);
        return id;
    }

    private UUID insertComment(UUID postId, UUID ownerId) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO comments "
                        + "(id, post_id, user_id, depth, content, moderation_status) "
                        + "VALUES (?, ?, ?, 0, 'Moderation target', 'approved')",
                id,
                postId,
                ownerId);
        return id;
    }

    private UUID insertReport(UUID reporterId, UUID targetId) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO reports "
                        + "(id, reporter_id, report_type, report_reason, entity_id, status) "
                        + "VALUES (?, ?, 'user', 'spam', ?, 'pending')",
                id,
                reporterId,
                targetId);
        return id;
    }

    private int auditCount(String actionType, UUID targetUserId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM admin_actions "
                        + "WHERE action_type = CAST(? AS admin_action_type) AND target_user_id = ?",
                Integer.class,
                actionType,
                targetUserId);
    }

    private ResponseEntity<Map> patch(String path, Object body, TestUser user) {
        return rest.exchange(
                path, HttpMethod.PATCH, new HttpEntity<>(body, authHeaders(user)), Map.class);
    }

    private ResponseEntity<Map> get(String path, TestUser user) {
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(authHeaders(user)), Map.class);
    }

    private HttpHeaders authHeaders(TestUser user) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(user.token());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> dataOf(ResponseEntity<Map> response) {
        return (Map<String, Object>) response.getBody().get("data");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<?, ?>> contentOf(ResponseEntity<Map> response) {
        Map<?, ?> data = (Map<?, ?>) response.getBody().get("data");
        return (List<Map<?, ?>>) data.get("content");
    }
}
