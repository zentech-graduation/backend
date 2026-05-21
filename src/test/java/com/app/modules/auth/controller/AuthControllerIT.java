package com.app.modules.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.app.common.enums.ApiSuccessCode;
import com.app.common.response.ApiResponse;
import com.app.common.security.util.SecurityUtils;
import com.app.modules.auth.entity.RefreshToken;
import com.app.modules.auth.enums.UserStatus;
import com.app.modules.auth.repository.RefreshTokenRepository;
import com.app.modules.auth.repository.UserRepository;
import com.app.modules.mail.service.MailService;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.profiles.active=dev",
            "spring.docker.compose.enabled=false",
            "spring.autoconfigure.exclude="
                    + "org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration"
        })
@Testcontainers
@AutoConfigureTestRestTemplate
@Import(AuthControllerIT.IntegrationTestConfig.class)
class AuthControllerIT {

    private static final String TEST_JWT_SECRET = "integration-test-secret-32-chars-minimum-len!!";
    private static final String TEST_JWT_ISSUER = "https://it.test.local";
    private static final String TEST_JWT_AUDIENCE = "App";

    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    @DynamicPropertySource
    static void register(DynamicPropertyRegistry r) {
        r.add("spring.data.redis.host", redis::getHost);
        r.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        r.add("JWT_SECRET", () -> TEST_JWT_SECRET);
        r.add("JWT_ISSUER", () -> TEST_JWT_ISSUER);
        r.add("JWT_AUDIENCE", () -> TEST_JWT_AUDIENCE);
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
    @Autowired private UserRepository userRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;

    @MockitoBean private MailService mailService;

    @Test
    void refresh_bannedUser_returns403AndOldTokenIsDurablyRevoked() {
        String email = uniqueEmail("refresh_banned");
        Map<?, ?> sessionData = registerVerifyAndLogin("user_bnnd", email, "password1");
        String oldRefreshToken = (String) sessionData.get("refreshToken");

        userRepository
                .findByEmailAndDeletedAtIsNull(email)
                .ifPresent(
                        u -> {
                            u.setStatus(UserStatus.BANNED);
                            userRepository.save(u);
                        });

        ResponseEntity<Map> response =
                postJson("/api/v1/auth/refresh", Map.of("refreshToken", oldRefreshToken));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().get("code")).isEqualTo("AUTH_ACCOUNT_LOCKED");

        // Critical assertion: the old token row must have revoked_at committed even though
        // the outer refresh transaction threw. Proves REQUIRES_NEW on rotate() is in effect.
        RefreshToken oldRow =
                refreshTokenRepository
                        .findByTokenHash(sha256(oldRefreshToken))
                        .orElseThrow(
                                () ->
                                        new AssertionError(
                                                "old refresh_tokens row was deleted — expected"
                                                        + " revoked_at to be set instead"));
        assertThat(oldRow.getRevokedAt())
                .as("old refresh token must have revoked_at set (durable revocation)")
                .isNotNull();
    }

    @Test
    void refresh_suspendedUser_returns403AndOldTokenIsDurablyRevoked() {
        String email = uniqueEmail("refresh_susp");
        Map<?, ?> sessionData = registerVerifyAndLogin("user_susp", email, "password1");
        String oldRefreshToken = (String) sessionData.get("refreshToken");

        userRepository
                .findByEmailAndDeletedAtIsNull(email)
                .ifPresent(
                        u -> {
                            u.setStatus(UserStatus.SUSPENDED);
                            userRepository.save(u);
                        });

        ResponseEntity<Map> response =
                postJson("/api/v1/auth/refresh", Map.of("refreshToken", oldRefreshToken));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().get("code")).isEqualTo("AUTH_ACCOUNT_INACTIVE");

        RefreshToken oldRow =
                refreshTokenRepository
                        .findByTokenHash(sha256(oldRefreshToken))
                        .orElseThrow(
                                () ->
                                        new AssertionError(
                                                "old refresh_tokens row was deleted — expected"
                                                        + " revoked_at to be set instead"));
        assertThat(oldRow.getRevokedAt())
                .as("old refresh token must have revoked_at set (durable revocation)")
                .isNotNull();
    }

    @Test
    void register_validPayload_returns201WithNoTokens() {
        String email = uniqueEmail("ok");
        ResponseEntity<Map> response =
                postJson("/api/v1/auth/register", registerBody("user_ok", email, "password1"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().get("data")).isNull();
    }

    @Test
    void register_duplicateEmail_returns409() {
        String email = uniqueEmail("dup");
        postJson("/api/v1/auth/register", registerBody("user_dup1", email, "password1"));

        ResponseEntity<Map> second =
                postJson("/api/v1/auth/register", registerBody("user_dup2", email, "password1"));

        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void register_invalidEmail_returns400() {
        ResponseEntity<Map> response =
                postJson(
                        "/api/v1/auth/register",
                        registerBody("user_invalid", "not-an-email", "password1"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void register_shortPassword_returns400() {
        ResponseEntity<Map> response =
                postJson(
                        "/api/v1/auth/register",
                        registerBody("user_short", uniqueEmail("short"), "short"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void login_unverifiedEmail_returns403() {
        String email = uniqueEmail("login_unverified");
        postJson("/api/v1/auth/register", registerBody("user_unverified", email, "password1"));

        ResponseEntity<Map> response =
                postJson("/api/v1/auth/login", Map.of("email", email, "password", "password1"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(response.getBody().get("code")).isEqualTo("AUTH_ACCOUNT_INACTIVE");
    }

    @Test
    void login_correctCredentials_returns200WithTokens() {
        String email = uniqueEmail("login_ok");
        postJson("/api/v1/auth/register", registerBody("user_login", email, "password1"));
        String token = captureLatestVerificationToken(email);
        rest.getForEntity("/api/v1/auth/verify-email?token=" + token, Map.class);

        ResponseEntity<Map> response =
                postJson("/api/v1/auth/login", Map.of("email", email, "password", "password1"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Map<?, ?>) response.getBody().get("data")).get("accessToken"))
                .asString()
                .isNotBlank();
    }

    @Test
    void login_wrongPassword_returns401() {
        String email = uniqueEmail("wrong_pw");
        postJson("/api/v1/auth/register", registerBody("user_wpw", email, "password1"));

        ResponseEntity<Map> response =
                postJson("/api/v1/auth/login", Map.of("email", email, "password", "WRONG-PW"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().get("code")).isEqualTo("AUTH_INVALID_CREDENTIALS");
    }

    @Test
    void login_unknownEmail_returns401SameCodeAsWrongPassword() {
        ResponseEntity<Map> response =
                postJson(
                        "/api/v1/auth/login",
                        Map.of("email", uniqueEmail("ghost"), "password", "password1"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().get("code")).isEqualTo("AUTH_INVALID_CREDENTIALS");
    }

    @Test
    void refresh_validToken_returns200WithNewPair() {
        String email = uniqueEmail("refresh_ok");
        Map<?, ?> sessionData = registerVerifyAndLogin("user_refr", email, "password1");
        String refresh = (String) sessionData.get("refreshToken");

        ResponseEntity<Map> response =
                postJson("/api/v1/auth/refresh", Map.of("refreshToken", refresh));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> data = (Map<?, ?>) response.getBody().get("data");
        assertThat(data.get("accessToken")).asString().isNotBlank();
        assertThat(data.get("refreshToken")).asString().isNotBlank();
        assertThat(data.get("refreshToken")).isNotEqualTo(refresh);
    }

    @Test
    void refresh_revokedToken_returns401() {
        String email = uniqueEmail("refresh_revoked");
        Map<?, ?> regData = registerVerifyAndLogin("user_rrv", email, "password1");
        String refresh = (String) regData.get("refreshToken");
        String access = (String) regData.get("accessToken");
        postJsonWithAuth("/api/v1/auth/logout", Map.of("refreshToken", refresh), access);

        ResponseEntity<Map> response =
                postJson("/api/v1/auth/refresh", Map.of("refreshToken", refresh));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().get("code")).isEqualTo("AUTH_REFRESH_TOKEN_INVALID");
    }

    @Test
    void refresh_alreadyUsedToken_returns401() {
        String email = uniqueEmail("refresh_used");
        String firstRefresh =
                (String)
                        registerVerifyAndLogin("user_rused", email, "password1")
                                .get("refreshToken");
        // First rotation succeeds and revokes the original.
        postJson("/api/v1/auth/refresh", Map.of("refreshToken", firstRefresh));

        // Replaying the original (now revoked) token must be rejected.
        ResponseEntity<Map> response =
                postJson("/api/v1/auth/refresh", Map.of("refreshToken", firstRefresh));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().get("code")).isEqualTo("AUTH_REFRESH_TOKEN_INVALID");
    }

    @Test
    void logout_returns204() {
        String email = uniqueEmail("logout");
        Map<?, ?> regData = registerVerifyAndLogin("user_lout", email, "password1");
        String refresh = (String) regData.get("refreshToken");
        String access = (String) regData.get("accessToken");

        ResponseEntity<Map> response =
                postJsonWithAuth("/api/v1/auth/logout", Map.of("refreshToken", refresh), access);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void logout_noAuthHeader_returns401() {
        String email = uniqueEmail("logout_noauth");
        String refresh =
                (String) registerVerifyAndLogin("user_lna", email, "password1").get("refreshToken");

        ResponseEntity<Map> response =
                postJson("/api/v1/auth/logout", Map.of("refreshToken", refresh));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void logout_validBearerToken_returns204() {
        String email = uniqueEmail("logout_auth");
        Map<?, ?> regData = registerVerifyAndLogin("user_lauth", email, "password1");
        String refresh = (String) regData.get("refreshToken");
        String access = (String) regData.get("accessToken");

        ResponseEntity<Map> response =
                postJsonWithAuth("/api/v1/auth/logout", Map.of("refreshToken", refresh), access);

        assertThat(response.getStatusCode()).isIn(HttpStatus.OK, HttpStatus.NO_CONTENT);
    }

    @Test
    void login_noAuthHeader_isPubliclyReachable() {
        ResponseEntity<Map> response =
                postJson(
                        "/api/v1/auth/login",
                        Map.of("email", uniqueEmail("login_pub"), "password", "password1"));

        // The endpoint is public; Spring Security must not block with 401-because-no-bearer.
        // A 401 here means bad credentials, not a missing token — that's still acceptable.
        assertThat(response.getStatusCode().value()).isNotEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(response.getStatusCode()).isNotEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @Test
    void register_noAuthHeader_isPubliclyReachable() {
        String email = uniqueEmail("reg_pub");
        ResponseEntity<Map> response =
                postJson("/api/v1/auth/register", registerBody("user_regpub", email, "password1"));

        assertThat(response.getStatusCode())
                .isIn(HttpStatus.CREATED, HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void protectedEndpoint_validJwt_returns200() {
        String email = uniqueEmail("prot_ok");
        String access =
                (String) registerVerifyAndLogin("user_prot", email, "password1").get("accessToken");

        ResponseEntity<Map> response = getWithAuth("/api/v1/test/me", access);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void protectedEndpoint_bannedUser_validAccessToken_returns401() {
        String email = uniqueEmail("prot_banned");
        String access =
                (String)
                        registerVerifyAndLogin("user_prtbn", email, "password1").get("accessToken");

        userRepository
                .findByEmailAndDeletedAtIsNull(email)
                .ifPresent(
                        u -> {
                            u.setStatus(UserStatus.BANNED);
                            userRepository.save(u);
                        });

        ResponseEntity<Map> response = getWithAuth("/api/v1/test/me", access);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void protectedEndpoint_expiredJwt_returns401() {
        String expired = mintTestJwt(Instant.now().minusSeconds(60));

        ResponseEntity<Map> response = getWithAuth("/api/v1/test/me", expired);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void protectedEndpoint_tamperedJwt_returns401() {
        String valid = mintTestJwt(Instant.now().plusSeconds(300));
        String[] parts = valid.split("\\.");
        char first = parts[2].charAt(0);
        char flipped = first == 'A' ? 'B' : 'A';
        String tampered = parts[0] + "." + parts[1] + "." + flipped + parts[2].substring(1);

        ResponseEntity<Map> response = getWithAuth("/api/v1/test/me", tampered);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void verifyEmail_invalidToken_returnsBadRequest() {
        ResponseEntity<Map> response =
                rest.getForEntity("/api/v1/auth/verify-email?token=does-not-exist", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code")).isEqualTo("AUTH_VERIFY_TOKEN_INVALID");
    }

    @Test
    void verifyEmail_consumedToken_returnsBadRequest() {
        String email = uniqueEmail("consumed");
        ResponseEntity<Map> reg =
                postJson(
                        "/api/v1/auth/register", registerBody("user_consumed", email, "password1"));
        assertThat(reg.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        String token = captureLatestVerificationToken(email);

        ResponseEntity<Map> first =
                rest.getForEntity("/api/v1/auth/verify-email?token=" + token, Map.class);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> second =
                rest.getForEntity("/api/v1/auth/verify-email?token=" + token, Map.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(second.getBody().get("code")).isEqualTo("AUTH_VERIFY_TOKEN_INVALID");
    }

    @Test
    void verifyEmail_unknownToken_returnsSameBadRequestAsConsumed() {
        ResponseEntity<Map> response =
                rest.getForEntity(
                        "/api/v1/auth/verify-email?token=" + UUID.randomUUID(), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("code")).isEqualTo("AUTH_VERIFY_TOKEN_INVALID");
    }

    @Test
    void verifyEmail_validToken_returns200WithTokens() {
        String email = uniqueEmail("verify_ok");
        postJson("/api/v1/auth/register", registerBody("user_vok", email, "password1"));
        String token = captureLatestVerificationToken(email);

        ResponseEntity<Map> response =
                rest.getForEntity("/api/v1/auth/verify-email?token=" + token, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        Map<?, ?> data = (Map<?, ?>) response.getBody().get("data");
        assertThat(data.get("accessToken")).asString().isNotBlank();
        assertThat(data.get("refreshToken")).asString().isNotBlank();
        assertThat(((Map<?, ?>) data.get("user")).get("emailVerified")).isEqualTo(true);
    }

    @Test
    void login_logout_reuseAccessToken_returns401() {
        String email = uniqueEmail("blacklist");
        Map<?, ?> data = registerVerifyAndLogin("user_bl", email, "password1");
        String access = (String) data.get("accessToken");
        String refresh = (String) data.get("refreshToken");

        ResponseEntity<Map> meBefore = getWithAuth("/api/v1/test/me", access);
        assertThat(meBefore.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> logout =
                postJsonWithAuth("/api/v1/auth/logout", Map.of("refreshToken", refresh), access);
        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<Map> reused = getWithAuth("/api/v1/test/me", access);

        assertThat(reused.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void login_wrongPassword_10timesSameIp_11thReturns429() {
        String forwardedIp = uniqueIp();
        String email = uniqueEmail("rl_login");
        postJson(
                "/api/v1/auth/register",
                registerBody("user_rllogin", email, "password1"),
                forwardedIp);

        for (int i = 0; i < 10; i++) {
            ResponseEntity<Map> response =
                    postJson(
                            "/api/v1/auth/login",
                            Map.of("email", email, "password", "WRONG"),
                            forwardedIp);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        ResponseEntity<Map> blocked =
                postJson(
                        "/api/v1/auth/login",
                        Map.of("email", email, "password", "WRONG"),
                        forwardedIp);

        assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(blocked.getBody().get("code")).isEqualTo("TOO_MANY_REQUESTS");
    }

    @Test
    void forgotPassword_3timesSameIp_4thReturns429() {
        String forwardedIp = uniqueIp();

        for (int i = 0; i < 3; i++) {
            ResponseEntity<Map> response =
                    postJson(
                            "/api/v1/auth/forgot-password",
                            Map.of("email", uniqueEmail("rl_forgot")),
                            forwardedIp);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        ResponseEntity<Map> blocked =
                postJson(
                        "/api/v1/auth/forgot-password",
                        Map.of("email", uniqueEmail("rl_forgot")),
                        forwardedIp);

        assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(blocked.getBody().get("code")).isEqualTo("TOO_MANY_REQUESTS");
    }

    @Test
    void forgotPassword_unknownEmail_returns200NoLeak() {
        ResponseEntity<Map> response =
                postJson(
                        "/api/v1/auth/forgot-password",
                        Map.of("email", uniqueEmail("forgot_ghost")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void login_pastRateLimit_returns429WithRetryAfterHeader() {
        String forwardedIp = uniqueIp();
        String email = uniqueEmail("rl_retry_after");
        postJson(
                "/api/v1/auth/register", registerBody("user_rra", email, "password1"), forwardedIp);

        for (int i = 0; i < 10; i++) {
            postJson(
                    "/api/v1/auth/login", Map.of("email", email, "password", "WRONG"), forwardedIp);
        }

        ResponseEntity<Map> blocked =
                postJson(
                        "/api/v1/auth/login",
                        Map.of("email", email, "password", "WRONG"),
                        forwardedIp);

        assertThat(blocked.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        String retryAfter =
                blocked.getHeaders().getFirst(org.springframework.http.HttpHeaders.RETRY_AFTER);
        assertThat(retryAfter).as("429 response must include Retry-After header").isNotNull();
        assertThat(Long.parseLong(retryAfter))
                .as("Retry-After must be a positive number of seconds")
                .isGreaterThan(0);
    }

    @Test
    void login_bodyExceedsMaxLoginBodyBytes_returns400WithApiResponseEnvelope() {
        // Default maxLoginBodyBytes is 2048; build ~5KB of JSON padding.
        StringBuilder padding = new StringBuilder(5000);
        for (int i = 0; i < 5000; i++) {
            padding.append('x');
        }
        String body =
                "{\"email\":\"foo@example.com\",\"password\":\"p\",\"junk\":\"" + padding + "\"}";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<Map> response =
                rest.exchange(
                        "/api/v1/auth/login",
                        HttpMethod.POST,
                        new HttpEntity<>(body, headers),
                        Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("code")).isEqualTo("BAD_REQUEST");
        assertThat(response.getBody().get("success")).isEqualTo(false);
    }

    @Test
    void authController_hasNoRateLimiterAnnotations() {
        Method[] methods = AuthController.class.getDeclaredMethods();
        for (Method m : methods) {
            assertThat(
                            m.getAnnotation(
                                    io.github.resilience4j.ratelimiter.annotation.RateLimiter
                                            .class))
                    .as(
                            "Method %s should not carry @RateLimiter (now handled by"
                                    + " AuthRateLimitFilter)",
                            m.getName())
                    .isNull();
        }
    }

    private ResponseEntity<Map> postJson(String path, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
    }

    private ResponseEntity<Map> postJson(String path, Object body, String forwardedIp) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Forwarded-For", forwardedIp);
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
    }

    private ResponseEntity<Map> postJsonWithAuth(String path, Object body, String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
    }

    private ResponseEntity<Map> getWithAuth(String path, String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
    }

    private Map<?, ?> registerVerifyAndLogin(String username, String email, String password) {
        postJson("/api/v1/auth/register", registerBody(username, email, password));
        String verToken = captureLatestVerificationToken(email);
        rest.getForEntity("/api/v1/auth/verify-email?token=" + verToken, Map.class);
        ResponseEntity<Map> login =
                postJson("/api/v1/auth/login", Map.of("email", email, "password", password));
        return (Map<?, ?>) login.getBody().get("data");
    }

    private String captureLatestVerificationToken(String email) {
        org.mockito.ArgumentCaptor<String> urlCaptor =
                org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(mailService, org.mockito.Mockito.atLeastOnce())
                .sendEmailVerification(
                        org.mockito.ArgumentMatchers.eq(email),
                        org.mockito.ArgumentMatchers.anyString(),
                        urlCaptor.capture());
        String url = urlCaptor.getAllValues().get(urlCaptor.getAllValues().size() - 1);
        int idx = url.indexOf("token=");
        return url.substring(idx + "token=".length());
    }

    private static Map<String, String> registerBody(
            String username, String email, String password) {
        return Map.of("username", username, "email", email, "password", password);
    }

    private static String uniqueEmail(String tag) {
        return tag + "_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }

    private static String uniqueIp() {
        // Build a 10.x.x.x address that is unique per test method so rate-limit buckets do not
        // bleed between scenarios. The window is 15 minutes so a fresh IP guarantees a clean
        // counter.
        java.util.Random rand = new java.util.Random();
        return "10." + rand.nextInt(256) + "." + rand.nextInt(256) + "." + (1 + rand.nextInt(254));
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String mintTestJwt(Instant expiresAt) {
        SecretKey key =
                new SecretKeySpec(TEST_JWT_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        Instant issuedAt = expiresAt.minusSeconds(60);
        JwtClaimsSet claims =
                JwtClaimsSet.builder()
                        .issuer(TEST_JWT_ISSUER)
                        .audience(java.util.List.of(TEST_JWT_AUDIENCE))
                        .subject(UUID.randomUUID().toString())
                        .claim("email", "x@example.com")
                        .claim("role", "USER")
                        .issuedAt(issuedAt)
                        .notBefore(issuedAt)
                        .expiresAt(expiresAt)
                        .build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    @TestConfiguration
    static class IntegrationTestConfig {

        @Bean
        TestProtectedEndpoint testProtectedEndpoint() {
            return new TestProtectedEndpoint();
        }
    }

    @RestController
    @RequestMapping("/api/v1/test")
    static class TestProtectedEndpoint {

        @GetMapping("/me")
        ResponseEntity<ApiResponse<UUID>> me() {
            return ResponseEntity.ok(
                    ApiResponse.success(ApiSuccessCode.OK, SecurityUtils.getCurrentUserId()));
        }
    }
}
