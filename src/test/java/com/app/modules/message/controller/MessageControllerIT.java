package com.app.modules.message.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.profiles.active=dev",
            "spring.docker.compose.enabled=false",
            "spring.autoconfigure.exclude=org.springframework.boot.amqp.autoconfigure.RabbitAutoConfiguration",
            "app.outbox.publisher.enabled=false",
            "app.post.seed.enabled=false",
            "app.hashtag.seed.enabled=false"
        })
@Testcontainers
@AutoConfigureTestRestTemplate
class MessageControllerIT {

    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    @DynamicPropertySource
    static void register(DynamicPropertyRegistry r) {
        r.add("spring.data.redis.host", redis::getHost);
        r.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        r.add("spring.data.redis.password", () -> "");
        r.add("JWT_SECRET", () -> "message-controller-it-secret-32-chars-min!!!!");
        r.add("JWT_ISSUER", () -> "https://message.it.local");
        r.add("JWT_AUDIENCE", () -> "App");
        r.add("ACCESS_TOKEN_TTL", () -> 900L);
        r.add("REFRESH_TOKEN_TTL", () -> 3600L);
        r.add("APP_BASE_URL", () -> "http://localhost:8080");
        r.add("CORS_ALLOWED_ORIGINS", () -> "http://localhost:3000");
        r.add("RESEND_API_KEY", () -> "re_test_dummy_key");
        r.add("MAIL_FROM_ADDRESS", () -> "noreply@test.local");
        r.add("MAIL_FROM_NAME", () -> "App IT");
        r.add("MAIL_APP_NAME", () -> "App");
        r.add("FRONTEND_BASE_URL", () -> "http://localhost:3000");
        r.add("GOOGLE_CLIENT_ID", () -> "test-client-id");
        r.add("GOOGLE_CLIENT_SECRET", () -> "test-client-secret");
        r.add("spring.datasource.hikari.data-source-properties.stringtype", () -> "unspecified");
    }

    @Autowired private TestRestTemplate rest;
    @Autowired private JdbcTemplate jdbcTemplate;

    private record TestUser(UUID id, String token) {}

    @AfterEach
    void cleanup() {
        jdbcTemplate.update("DELETE FROM messages");
        jdbcTemplate.update("DELETE FROM conversation_participants");
        jdbcTemplate.update("DELETE FROM conversations");
        jdbcTemplate.update("DELETE FROM follows");
        jdbcTemplate.update("DELETE FROM blocks");
        jdbcTemplate.update("DELETE FROM user_settings");
        jdbcTemplate.update("DELETE FROM refresh_tokens");
        jdbcTemplate.update("DELETE FROM oauth_accounts");
        jdbcTemplate.update("DELETE FROM user_credentials");
        jdbcTemplate.update("DELETE FROM outbox_events");
        jdbcTemplate.update("DELETE FROM users");
    }

    @Test
    void createDirectConversation_success_returnsConversationWithBothParticipants() {
        TestUser alice = registerUser("dm_alice");
        TestUser bob = registerUser("dm_bob");

        ResponseEntity<Map> response = createDirect(alice, bob.id());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<?, ?> data = (Map<?, ?>) response.getBody().get("data");
        assertThat(data.get("isGroup")).isEqualTo(false);
        List<Map<?, ?>> participants = (List<Map<?, ?>>) data.get("participants");
        assertThat(participants).hasSize(2);
        assertThat(activeParticipantIds(UUID.fromString((String) data.get("id"))))
                .containsExactlyInAnyOrder(alice.id(), bob.id());
    }

    @Test
    void createDirectConversation_calledTwice_reusesSameConversation() {
        TestUser alice = registerUser("dedup_alice");
        TestUser bob = registerUser("dedup_bob");

        ResponseEntity<Map> first = createDirect(alice, bob.id());
        ResponseEntity<Map> second = createDirect(bob, alice.id());

        String firstId = (String) ((Map<?, ?>) first.getBody().get("data")).get("id");
        String secondId = (String) ((Map<?, ?>) second.getBody().get("data")).get("id");
        assertThat(secondId).isEqualTo(firstId);
        assertThat(conversationCount()).isEqualTo(1);
    }

    @Test
    void createDirectConversation_concurrentCallsForSamePair_createExactlyOneConversation()
            throws Exception {
        TestUser alice = registerUser("race_alice");
        TestUser bob = registerUser("race_bob");
        int callers = 8;
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        CountDownLatch ready = new CountDownLatch(callers);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<ResponseEntity<Map>>> futures = new ArrayList<>();
            for (int i = 0; i < callers; i++) {
                TestUser caller = (i % 2 == 0) ? alice : bob;
                UUID target = (i % 2 == 0) ? bob.id() : alice.id();
                futures.add(
                        pool.submit(
                                () -> {
                                    ready.countDown();
                                    start.await();
                                    return createDirect(caller, target);
                                }));
            }
            ready.await();
            start.countDown();

            Set<String> conversationIds = new HashSet<>();
            for (Future<ResponseEntity<Map>> future : futures) {
                ResponseEntity<Map> response = future.get(30, TimeUnit.SECONDS);
                assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
                conversationIds.add(conversationIdOf(response).toString());
            }

            assertThat(conversationIds).hasSize(1);
            assertThat(conversationCount()).isEqualTo(1);
            assertThat(activeParticipantIds(UUID.fromString(conversationIds.iterator().next())))
                    .containsExactlyInAnyOrder(alice.id(), bob.id());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void createDirectConversation_blockedTarget_returnsForbidden() {
        TestUser alice = registerUser("block_alice");
        TestUser bob = registerUser("block_bob");
        jdbcTemplate.update(
                "INSERT INTO blocks (blocker_id, blocked_id) VALUES (?, ?)", bob.id(), alice.id());

        ResponseEntity<Map> response = createDirect(alice, bob.id());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().get("code")).isEqualTo("SOCIAL_BLOCKED");
    }

    @Test
    void createDirectConversation_targetNotFound_returnsNotFound() {
        TestUser alice = registerUser("missing_target_alice");

        ResponseEntity<Map> response = createDirect(alice, UUID.randomUUID());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().get("code")).isEqualTo("USER_NOT_FOUND");
    }

    @Test
    void createDirectConversation_requestsDisabledAndNotFollowedBack_returnsForbidden() {
        TestUser alice = registerUser("req_alice");
        TestUser bob = registerUser("req_bob");
        jdbcTemplate.update(
                "UPDATE user_settings SET allow_message_requests = FALSE WHERE user_id = ?",
                bob.id());

        ResponseEntity<Map> response = createDirect(alice, bob.id());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().get("code")).isEqualTo("MESSAGE_REQUEST_NOT_ALLOWED");
    }

    @Test
    void createGroupConversation_success_creatorIsAdminAndMembersAreNot() {
        TestUser owner = registerUser("group_owner");
        TestUser member1 = registerUser("group_member1");
        TestUser member2 = registerUser("group_member2");

        ResponseEntity<Map> response =
                rest.exchange(
                        "/api/v1/conversations/group",
                        HttpMethod.POST,
                        new HttpEntity<>(
                                Map.of(
                                        "groupName",
                                        "Trip Planning",
                                        "participantIds",
                                        List.of(member1.id().toString(), member2.id().toString())),
                                authHeaders(owner)),
                        Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Map<?, ?> data = (Map<?, ?>) response.getBody().get("data");
        UUID conversationId = UUID.fromString((String) data.get("id"));
        assertThat(data.get("isGroup")).isEqualTo(true);
        assertThat(isAdmin(conversationId, owner.id())).isTrue();
        assertThat(isAdmin(conversationId, member1.id())).isFalse();
        assertThat(isAdmin(conversationId, member2.id())).isFalse();

        ResponseEntity<Map> detail = getWithAuth("/api/v1/conversations/" + conversationId, owner);
        Map<?, ?> detailData = (Map<?, ?>) detail.getBody().get("data");
        assertThat(detailData.get("isGroup")).isEqualTo(true);

        ResponseEntity<Map> list = getWithAuth("/api/v1/conversations", owner);
        List<Map<?, ?>> listContent =
                (List<Map<?, ?>>) ((Map<?, ?>) list.getBody().get("data")).get("content");
        Map<?, ?> listEntry =
                listContent.stream()
                        .filter(entry -> conversationId.toString().equals(entry.get("id")))
                        .findFirst()
                        .orElseThrow();
        assertThat(listEntry.get("isGroup")).isEqualTo(true);
    }

    @Test
    void listMyConversations_returnsCreatedConversation() {
        TestUser alice = registerUser("list_alice");
        TestUser bob = registerUser("list_bob");
        createDirect(alice, bob.id());

        ResponseEntity<Map> response = getWithAuth("/api/v1/conversations", alice);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> data = (Map<?, ?>) response.getBody().get("data");
        List<Map<?, ?>> content = (List<Map<?, ?>>) data.get("content");
        assertThat(content).hasSize(1);
        assertThat(content.get(0).get("unreadCount")).isEqualTo(0);
    }

    @Test
    void listMyConversations_cursorAcrossNullLastMessageAtRows_paginatesWithoutError() {
        TestUser alice = registerUser("nullcursor_alice");
        TestUser bob = registerUser("nullcursor_bob");
        TestUser carol = registerUser("nullcursor_carol");
        UUID conv1 = conversationIdOf(createDirect(alice, bob.id()));
        UUID conv2 = conversationIdOf(createDirect(alice, carol.id()));

        ResponseEntity<Map> firstPage = getWithAuth("/api/v1/conversations?limit=1", alice);

        assertThat(firstPage.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> firstData = (Map<?, ?>) firstPage.getBody().get("data");
        List<Map<?, ?>> firstContent = (List<Map<?, ?>>) firstData.get("content");
        assertThat(firstContent).hasSize(1);
        Map<?, ?> firstPageInfo = (Map<?, ?>) firstData.get("pageInfo");
        String endCursor = (String) firstPageInfo.get("endCursor");
        assertThat(endCursor).isNotBlank();

        ResponseEntity<Map> secondPage =
                getWithAuth("/api/v1/conversations?limit=1&cursor=" + endCursor, alice);

        assertThat(secondPage.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> secondData = (Map<?, ?>) secondPage.getBody().get("data");
        List<Map<?, ?>> secondContent = (List<Map<?, ?>>) secondData.get("content");
        assertThat(secondContent).hasSize(1);
        Set<String> seenIds =
                Set.of(
                        (String) firstContent.get(0).get("id"),
                        (String) secondContent.get(0).get("id"));
        assertThat(seenIds).containsExactlyInAnyOrder(conv1.toString(), conv2.toString());
    }

    @Test
    void getConversation_nonParticipant_returnsForbidden() {
        TestUser alice = registerUser("detail_alice");
        TestUser bob = registerUser("detail_bob");
        TestUser stranger = registerUser("detail_stranger");
        UUID conversationId = conversationIdOf(createDirect(alice, bob.id()));

        ResponseEntity<Map> response =
                getWithAuth("/api/v1/conversations/" + conversationId, stranger);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().get("code")).isEqualTo("CONVERSATION_FORBIDDEN");
    }

    @Test
    void getConversation_notFound_returnsNotFound() {
        TestUser alice = registerUser("notfound_alice");

        ResponseEntity<Map> response =
                getWithAuth("/api/v1/conversations/" + UUID.randomUUID(), alice);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().get("code")).isEqualTo("CONVERSATION_NOT_FOUND");
    }

    @Test
    void updateGroup_byAdmin_updatesNameAndAvatar() {
        TestUser owner = registerUser("rename_owner");
        TestUser member = registerUser("rename_member");
        UUID conversationId = createGroup(owner, "Old Name", member.id());

        ResponseEntity<Map> response =
                rest.exchange(
                        "/api/v1/conversations/" + conversationId,
                        HttpMethod.PATCH,
                        new HttpEntity<>(
                                Map.of(
                                        "groupName",
                                        "New Name",
                                        "groupAvatarUrl",
                                        "https://cdn.test/g.png"),
                                authHeaders(owner)),
                        Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> data = (Map<?, ?>) response.getBody().get("data");
        assertThat(data.get("groupName")).isEqualTo("New Name");
        assertThat(data.get("groupAvatarUrl")).isEqualTo("https://cdn.test/g.png");
    }

    @Test
    void updateGroup_byNonAdmin_returnsForbidden() {
        TestUser owner = registerUser("rename2_owner");
        TestUser member = registerUser("rename2_member");
        UUID conversationId = createGroup(owner, "Old Name", member.id());

        ResponseEntity<Map> response =
                rest.exchange(
                        "/api/v1/conversations/" + conversationId,
                        HttpMethod.PATCH,
                        new HttpEntity<>(Map.of("groupName", "Hijacked"), authHeaders(member)),
                        Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().get("code")).isEqualTo("GROUP_ADMIN_REQUIRED");
    }

    @Test
    void addParticipants_byAdmin_addsNewMember() {
        TestUser owner = registerUser("add_owner");
        TestUser existing = registerUser("add_existing");
        TestUser newcomer = registerUser("add_newcomer");
        UUID conversationId = createGroup(owner, "Growing Group", existing.id());

        ResponseEntity<Map> response =
                rest.exchange(
                        "/api/v1/conversations/" + conversationId + "/participants",
                        HttpMethod.POST,
                        new HttpEntity<>(
                                Map.of("userIds", List.of(newcomer.id().toString())),
                                authHeaders(owner)),
                        Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(activeParticipantIds(conversationId))
                .containsExactlyInAnyOrder(owner.id(), existing.id(), newcomer.id());
    }

    @Test
    void addParticipants_onDirectConversation_returnsConflict() {
        TestUser alice = registerUser("notgroup_alice");
        TestUser bob = registerUser("notgroup_bob");
        TestUser stranger = registerUser("notgroup_stranger");
        UUID conversationId = conversationIdOf(createDirect(alice, bob.id()));

        ResponseEntity<Map> response =
                rest.exchange(
                        "/api/v1/conversations/" + conversationId + "/participants",
                        HttpMethod.POST,
                        new HttpEntity<>(
                                Map.of("userIds", List.of(stranger.id().toString())),
                                authHeaders(alice)),
                        Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().get("code")).isEqualTo("CONVERSATION_NOT_GROUP");
    }

    @Test
    void removeParticipant_byAdmin_removesMember() {
        TestUser owner = registerUser("remove_owner");
        TestUser member = registerUser("remove_member");
        UUID conversationId = createGroup(owner, "Shrinking Group", member.id());

        ResponseEntity<Map> response =
                rest.exchange(
                        "/api/v1/conversations/" + conversationId + "/participants/" + member.id(),
                        HttpMethod.DELETE,
                        new HttpEntity<>(authHeaders(owner)),
                        Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(activeParticipantIds(conversationId)).containsExactly(owner.id());
    }

    @Test
    void leaveConversation_lastAdmin_promotesRemainingMemberAndIsIdempotent() {
        TestUser owner = registerUser("leave_owner");
        TestUser member = registerUser("leave_member");
        UUID conversationId = createGroup(owner, "Handoff Group", member.id());

        ResponseEntity<Map> firstLeave =
                rest.exchange(
                        "/api/v1/conversations/" + conversationId + "/leave",
                        HttpMethod.POST,
                        new HttpEntity<>(authHeaders(owner)),
                        Map.class);
        assertThat(firstLeave.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(isAdmin(conversationId, member.id())).isTrue();
        assertThat(activeParticipantIds(conversationId)).containsExactly(member.id());

        ResponseEntity<Map> secondLeave =
                rest.exchange(
                        "/api/v1/conversations/" + conversationId + "/leave",
                        HttpMethod.POST,
                        new HttpEntity<>(authHeaders(owner)),
                        Map.class);
        assertThat(secondLeave.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private ResponseEntity<Map> createDirect(TestUser actor, UUID targetId) {
        return rest.exchange(
                "/api/v1/conversations",
                HttpMethod.POST,
                new HttpEntity<>(Map.of("targetUserId", targetId.toString()), authHeaders(actor)),
                Map.class);
    }

    private UUID createGroup(TestUser owner, String groupName, UUID... memberIds) {
        List<String> ids = List.of(memberIds).stream().map(UUID::toString).toList();
        ResponseEntity<Map> response =
                rest.exchange(
                        "/api/v1/conversations/group",
                        HttpMethod.POST,
                        new HttpEntity<>(
                                Map.of("groupName", groupName, "participantIds", ids),
                                authHeaders(owner)),
                        Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return conversationIdOf(response);
    }

    private static UUID conversationIdOf(ResponseEntity<Map> response) {
        Map<?, ?> data = (Map<?, ?>) response.getBody().get("data");
        return UUID.fromString((String) data.get("id"));
    }

    private int conversationCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM conversations", Integer.class);
    }

    private List<UUID> activeParticipantIds(UUID conversationId) {
        return jdbcTemplate.queryForList(
                "SELECT user_id FROM conversation_participants"
                        + " WHERE conversation_id = ? AND left_at IS NULL",
                UUID.class,
                conversationId);
    }

    private boolean isAdmin(UUID conversationId, UUID userId) {
        Boolean admin =
                jdbcTemplate.queryForObject(
                        "SELECT is_admin FROM conversation_participants"
                                + " WHERE conversation_id = ? AND user_id = ?",
                        Boolean.class,
                        conversationId,
                        userId);
        return Boolean.TRUE.equals(admin);
    }

    private TestUser registerUser(String username) {
        String email = username + "@test.local";
        String password = "S3cur3P@ssword!";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Forwarded-For", uniqueIp());

        Map<String, Object> registerPayload =
                Map.of(
                        "username", username,
                        "email", email,
                        "password", password,
                        "displayName", username);
        ResponseEntity<Map> reg =
                rest.exchange(
                        "/api/v1/auth/register",
                        HttpMethod.POST,
                        new HttpEntity<>(registerPayload, headers),
                        Map.class);
        assertThat(reg.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        UUID id =
                jdbcTemplate.queryForObject(
                        "SELECT id FROM users WHERE username = ?", UUID.class, username);
        jdbcTemplate.update(
                "UPDATE user_credentials SET email_verified = TRUE WHERE user_id = ?", id);

        ResponseEntity<Map> login =
                rest.exchange(
                        "/api/v1/auth/login",
                        HttpMethod.POST,
                        new HttpEntity<>(Map.of("email", email, "password", password), headers),
                        Map.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> data = (Map<?, ?>) login.getBody().get("data");
        return new TestUser(id, (String) data.get("accessToken"));
    }

    private HttpHeaders authHeaders(TestUser user) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(user.token());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private ResponseEntity<Map> getWithAuth(String url, TestUser user) {
        return rest.exchange(url, HttpMethod.GET, new HttpEntity<>(authHeaders(user)), Map.class);
    }

    private static String uniqueIp() {
        Random rand = new Random();
        return "10." + rand.nextInt(256) + "." + rand.nextInt(256) + "." + (1 + rand.nextInt(254));
    }
}
