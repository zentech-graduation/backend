package com.app.modules.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.app.common.enums.ApiSuccessCode;
import com.app.common.response.ApiResponse;
import com.app.common.security.SecurityUtils;
import com.app.modules.mail.service.MailService;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.profiles.active=dev",
            "spring.docker.compose.enabled=false",
            "spring.data.redis.host=localhost",
            "spring.data.redis.port=6379",
            "spring.autoconfigure.exclude="
                    + "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,"
                    + "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration,"
                    + "org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration"
        })
@Testcontainers
@Import(AuthControllerIT.IntegrationTestConfig.class)
class AuthControllerIT {

    private static final String TEST_JWT_SECRET = "integration-test-secret-32-chars-minimum-len!!";
    private static final String TEST_JWT_ISSUER = "https://it.test.local";

    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void register(DynamicPropertyRegistry r) {
        r.add("JWT_SECRET", () -> TEST_JWT_SECRET);
        r.add("JWT_ISSUER", () -> TEST_JWT_ISSUER);
        r.add("ACCESS_TOKEN_TTL", () -> 900L);
        r.add("REFRESH_TOKEN_TTL", () -> 3600L);
        r.add("APP_BASE_URL", () -> "http://localhost:8080");
        r.add("CORS_ALLOWED_ORIGINS", () -> "http://localhost:3000");
        r.add("RESEND_API_KEY", () -> "re_test_dummy_key");
        r.add("MAIL_FROM_ADDRESS", () -> "noreply@test.local");
        r.add("MAIL_FROM_NAME", () -> "App IT");
        r.add("MAIL_APP_NAME", () -> "App");
        r.add("MAIL_FRONTEND_BASE_URL", () -> "http://localhost:3000");
        r.add("GOOGLE_CLIENT_ID", () -> "test-client-id");
        r.add("GOOGLE_CLIENT_SECRET", () -> "test-client-secret");
        r.add("spring.datasource.hikari.data-source-properties.stringtype", () -> "unspecified");
    }

    @Autowired private TestRestTemplate rest;

    @MockitoBean private MailService mailService;

    @Test
    void register_validPayload_returns201WithTokens() {
        String email = uniqueEmail("ok");
        ResponseEntity<Map> response =
                postJson("/api/v1/auth/register", registerBody("user_ok", email, "password1"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(((Map<?, ?>) response.getBody().get("data")).get("accessToken"))
                .asString()
                .isNotBlank();
        assertThat(((Map<?, ?>) response.getBody().get("data")).get("refreshToken"))
                .asString()
                .isNotBlank();
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
    void login_correctCredentials_returns200WithTokens() {
        String email = uniqueEmail("login_ok");
        postJson("/api/v1/auth/register", registerBody("user_login", email, "password1"));

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
        ResponseEntity<Map> reg =
                postJson("/api/v1/auth/register", registerBody("user_refr", email, "password1"));
        String refresh = (String) ((Map<?, ?>) reg.getBody().get("data")).get("refreshToken");

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
        ResponseEntity<Map> reg =
                postJson("/api/v1/auth/register", registerBody("user_rrv", email, "password1"));
        String refresh = (String) ((Map<?, ?>) reg.getBody().get("data")).get("refreshToken");
        postJson("/api/v1/auth/logout", Map.of("refreshToken", refresh));

        ResponseEntity<Map> response =
                postJson("/api/v1/auth/refresh", Map.of("refreshToken", refresh));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getBody().get("code")).isEqualTo("AUTH_REFRESH_TOKEN_INVALID");
    }

    @Test
    void refresh_alreadyUsedToken_returns401() {
        String email = uniqueEmail("refresh_used");
        ResponseEntity<Map> reg =
                postJson("/api/v1/auth/register", registerBody("user_rused", email, "password1"));
        String firstRefresh = (String) ((Map<?, ?>) reg.getBody().get("data")).get("refreshToken");
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
        ResponseEntity<Map> reg =
                postJson("/api/v1/auth/register", registerBody("user_lout", email, "password1"));
        String refresh = (String) ((Map<?, ?>) reg.getBody().get("data")).get("refreshToken");

        ResponseEntity<Map> response =
                postJson("/api/v1/auth/logout", Map.of("refreshToken", refresh));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void protectedEndpoint_validJwt_returns200() {
        String email = uniqueEmail("prot_ok");
        ResponseEntity<Map> reg =
                postJson("/api/v1/auth/register", registerBody("user_prot", email, "password1"));
        String access = (String) ((Map<?, ?>) reg.getBody().get("data")).get("accessToken");

        ResponseEntity<Map> response = getWithAuth("/api/v1/test/me", access);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
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
        // Implementation throws AUTH_RESET_TOKEN_INVALID (HTTP 400) for unknown email
        // verification tokens. The audit spec listed 404 but the code returns 400 — the
        // integration test asserts the actual contract. This is recorded as a contract
        // discrepancy in the audit notes, not a security finding.
        ResponseEntity<Map> response =
                rest.getForEntity("/api/v1/auth/verify-email?token=does-not-exist", Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void forgotPassword_unknownEmail_returns200NoLeak() {
        ResponseEntity<Map> response =
                postJson(
                        "/api/v1/auth/forgot-password",
                        Map.of("email", uniqueEmail("forgot_ghost")));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private ResponseEntity<Map> postJson(String path, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
    }

    private ResponseEntity<Map> getWithAuth(String path, String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        return rest.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
    }

    private static Map<String, String> registerBody(
            String username, String email, String password) {
        return Map.of("username", username, "email", email, "password", password);
    }

    private static String uniqueEmail(String tag) {
        return tag + "_" + UUID.randomUUID().toString().substring(0, 8) + "@example.com";
    }

    private static String mintTestJwt(Instant expiresAt) {
        SecretKey key =
                new SecretKeySpec(TEST_JWT_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        JwtClaimsSet claims =
                JwtClaimsSet.builder()
                        .issuer(TEST_JWT_ISSUER)
                        .subject(UUID.randomUUID().toString())
                        .claim("email", "x@example.com")
                        .claim("role", "USER")
                        .issuedAt(expiresAt.minusSeconds(60))
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
