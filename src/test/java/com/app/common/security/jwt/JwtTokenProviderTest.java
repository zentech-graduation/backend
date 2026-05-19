package com.app.common.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import com.app.common.enums.ApiErrorCode;
import com.app.common.exception.AppException;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

class JwtTokenProviderTest {

    private static final String SECRET = "01234567890123456789012345678901-test-secret-32+chars";
    private static final String ISSUER = "https://issuer.test.local";
    private static final String AUDIENCE = "TestApp";

    private final JwtProperties properties =
            new JwtProperties(SECRET, ISSUER, AUDIENCE, 900, 2592000);
    private final JwtTokenProvider provider = new JwtTokenProvider(properties);

    @Test
    void generateAccessToken_returnsNonBlankCompactToken() {
        String token = provider.generateAccessToken(UUID.randomUUID(), "alice@example.com", "USER");

        assertThat(token).isNotBlank();
        assertThat(token.split("\\.")).hasSize(3);
    }

    @Test
    void validateAndParse_validToken_returnsCorrectClaims() {
        UUID userId = UUID.randomUUID();
        String token = provider.generateAccessToken(userId, "alice@example.com", "USER");

        JwtClaims claims = provider.validateAndParse(token);

        assertThat(claims.userId()).isEqualTo(userId);
        assertThat(claims.email()).isEqualTo("alice@example.com");
        assertThat(claims.role()).isEqualTo("USER");
    }

    @Test
    void generateAccessToken_containsJtiClaim() {
        String token = provider.generateAccessToken(UUID.randomUUID(), "x@example.com", "USER");

        JwtClaims claims = provider.validateAndParse(token);

        assertThat(claims.jti()).isNotNull();
        assertThat(claims.jti()).isNotBlank();
        assertThat(java.util.UUID.fromString(claims.jti())).isNotNull();
    }

    @Test
    void generateAccessToken_eachInvocationProducesUniqueJti() {
        String first = provider.generateAccessToken(UUID.randomUUID(), "x@example.com", "USER");
        String second = provider.generateAccessToken(UUID.randomUUID(), "x@example.com", "USER");

        JwtClaims firstClaims = provider.validateAndParse(first);
        JwtClaims secondClaims = provider.validateAndParse(second);

        assertThat(firstClaims.jti()).isNotEqualTo(secondClaims.jti());
    }

    @Test
    void validateAndParse_returnsJtiInClaims() {
        String token = provider.generateAccessToken(UUID.randomUUID(), "x@example.com", "USER");

        JwtClaims claims = provider.validateAndParse(token);

        assertThat(claims.jti()).isNotNull();
    }

    @Test
    void validateAndParse_returnsExpiresAtInClaims() {
        String token = provider.generateAccessToken(UUID.randomUUID(), "x@example.com", "USER");

        JwtClaims claims = provider.validateAndParse(token);

        assertThat(claims.expiresAt()).isNotNull();
        assertThat(claims.expiresAt()).isAfter(Instant.now());
    }

    @Test
    void validateAndParse_expiredToken_throwsAuthTokenExpired() {
        // Encode a token with negative TTL so exp is already in the past.
        String expiredToken =
                mintTokenWithExpiry(SECRET, ISSUER, AUDIENCE, Instant.now().minusSeconds(60));

        assertThatThrownBy(() -> provider.validateAndParse(expiredToken))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ApiErrorCode.AUTH_TOKEN_EXPIRED);
    }

    @Test
    void validateAndParse_tamperedSignature_throwsAuthTokenInvalid() {
        String token = provider.generateAccessToken(UUID.randomUUID(), "x@example.com", "USER");
        String[] parts = token.split("\\.");
        // Flip the first signature character to invalidate the HMAC without breaking the format.
        char first = parts[2].charAt(0);
        char flipped = first == 'A' ? 'B' : 'A';
        String tampered = parts[0] + "." + parts[1] + "." + flipped + parts[2].substring(1);

        assertThatThrownBy(() -> provider.validateAndParse(tampered))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ApiErrorCode.AUTH_TOKEN_INVALID);
    }

    @Test
    void validateAndParse_wrongAlgorithm_throwsAuthTokenInvalid() {
        // Sign with HS512 instead of HS256; the decoder is bound to HS256 and must reject.
        String hs512Token = mintHs512Token(SECRET, ISSUER);

        assertThatThrownBy(() -> provider.validateAndParse(hs512Token))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ApiErrorCode.AUTH_TOKEN_INVALID);
    }

    @Test
    void validateAndParse_wrongIssuer_throwsAuthTokenInvalid() {
        String token =
                mintTokenWithExpiry(
                        SECRET,
                        "https://other-issuer.local",
                        AUDIENCE,
                        Instant.now().plusSeconds(60));

        assertThatThrownBy(() -> provider.validateAndParse(token))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ApiErrorCode.AUTH_TOKEN_INVALID);
    }

    @Test
    void validateAndParse_nonUuidSubject_throwsAuthTokenInvalid() {
        Instant now = Instant.now();
        SecretKey key = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        JwtClaimsSet claims =
                JwtClaimsSet.builder()
                        .issuer(ISSUER)
                        .audience(List.of(AUDIENCE))
                        .subject("not-a-uuid")
                        .issuedAt(now)
                        .expiresAt(now.plusSeconds(60))
                        .build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        assertThatThrownBy(() -> provider.validateAndParse(token))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ApiErrorCode.AUTH_TOKEN_INVALID);
    }

    // --- Issuance: iss, aud, nbf ---

    @Test
    void generateAccessToken_containsIssuerClaim() {
        String token = provider.generateAccessToken(UUID.randomUUID(), "a@example.com", "USER");
        // Decode without validation to inspect raw claims.
        String payload =
                new String(
                        java.util.Base64.getUrlDecoder().decode(token.split("\\.")[1]),
                        StandardCharsets.UTF_8);
        assertThat(payload).contains("\"iss\"").contains(ISSUER);
    }

    @Test
    void generateAccessToken_containsAudienceClaim() {
        String token = provider.generateAccessToken(UUID.randomUUID(), "a@example.com", "USER");
        String payload =
                new String(
                        java.util.Base64.getUrlDecoder().decode(token.split("\\.")[1]),
                        StandardCharsets.UTF_8);
        assertThat(payload).contains("\"aud\"").contains(AUDIENCE);
    }

    @Test
    void generateAccessToken_nbfEqualToIat() {
        String token = provider.generateAccessToken(UUID.randomUUID(), "a@example.com", "USER");
        String payload =
                new String(
                        java.util.Base64.getUrlDecoder().decode(token.split("\\.")[1]),
                        StandardCharsets.UTF_8);
        // Both iat and nbf must be present; extract their numeric values and compare.
        long iat = extractLongClaim(payload, "iat");
        long nbf = extractLongClaim(payload, "nbf");
        assertThat(nbf).isEqualTo(iat);
    }

    // --- Validation: iss, aud, nbf ---

    @Test
    void validateAndParse_correctIssuerAndAud_accepted() {
        String token = mintTokenWithExpiry(SECRET, ISSUER, AUDIENCE, Instant.now().plusSeconds(60));
        // Should not throw.
        JwtClaims claims = provider.validateAndParse(token);
        assertThat(claims).isNotNull();
    }

    @Test
    void validateAndParse_missingIss_throwsAuthTokenInvalid() {
        // Build a token that intentionally omits the iss claim.
        Instant now = Instant.now();
        SecretKey key = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        JwtClaimsSet claims =
                JwtClaimsSet.builder()
                        .audience(List.of(AUDIENCE))
                        .subject(UUID.randomUUID().toString())
                        .claim("email", "a@example.com")
                        .claim("role", "USER")
                        .issuedAt(now)
                        .expiresAt(now.plusSeconds(60))
                        .build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        assertThatThrownBy(() -> provider.validateAndParse(token))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ApiErrorCode.AUTH_TOKEN_INVALID);
    }

    @Test
    void validateAndParse_wrongAudience_throwsAuthTokenInvalid() {
        String token =
                mintTokenWithExpiry(
                        SECRET, ISSUER, "wrong-audience", Instant.now().plusSeconds(60));

        assertThatThrownBy(() -> provider.validateAndParse(token))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ApiErrorCode.AUTH_TOKEN_INVALID);
    }

    @Test
    void validateAndParse_missingAud_throwsAuthTokenInvalid() {
        // Build a token without aud.
        Instant now = Instant.now();
        SecretKey key = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        JwtClaimsSet claims =
                JwtClaimsSet.builder()
                        .issuer(ISSUER)
                        .subject(UUID.randomUUID().toString())
                        .claim("email", "a@example.com")
                        .claim("role", "USER")
                        .issuedAt(now)
                        .expiresAt(now.plusSeconds(60))
                        .build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        assertThatThrownBy(() -> provider.validateAndParse(token))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ApiErrorCode.AUTH_TOKEN_INVALID);
    }

    @Test
    void validateAndParse_nbfInPast_accepted() {
        // nbf already in the past — must be accepted by JwtTimestampValidator.
        Instant now = Instant.now();
        SecretKey key = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        JwtClaimsSet claims =
                JwtClaimsSet.builder()
                        .issuer(ISSUER)
                        .audience(List.of(AUDIENCE))
                        .subject(UUID.randomUUID().toString())
                        .claim("email", "a@example.com")
                        .claim("role", "USER")
                        .issuedAt(now.minusSeconds(120))
                        .notBefore(now.minusSeconds(60))
                        .expiresAt(now.plusSeconds(300))
                        .build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        JwtClaims result = provider.validateAndParse(token);
        assertThat(result).isNotNull();
    }

    @Test
    void validateAndParse_nbfInFuture_throwsAuthTokenInvalid() {
        // nbf set 5 minutes in the future — JwtTimestampValidator must reject it.
        Instant now = Instant.now();
        SecretKey key = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        JwtClaimsSet claims =
                JwtClaimsSet.builder()
                        .issuer(ISSUER)
                        .audience(List.of(AUDIENCE))
                        .subject(UUID.randomUUID().toString())
                        .claim("email", "a@example.com")
                        .claim("role", "USER")
                        .issuedAt(now)
                        .notBefore(now.plusSeconds(300))
                        .expiresAt(now.plusSeconds(900))
                        .build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        assertThatThrownBy(() -> provider.validateAndParse(token))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(ApiErrorCode.AUTH_TOKEN_INVALID);
    }

    /** Extracts the numeric value of a simple JSON claim from a decoded JWT payload string. */
    private static long extractLongClaim(String jsonPayload, String claimName) {
        String search = "\"" + claimName + "\":";
        int idx = jsonPayload.indexOf(search);
        assertThat(idx)
                .as("claim '" + claimName + "' not found in payload")
                .isGreaterThanOrEqualTo(0);
        int start = idx + search.length();
        int end = start;
        while (end < jsonPayload.length()
                && (Character.isDigit(jsonPayload.charAt(end)) || jsonPayload.charAt(end) == '-')) {
            end++;
        }
        return Long.parseLong(jsonPayload.substring(start, end));
    }

    private static String mintTokenWithExpiry(
            String secret, String issuer, String audience, Instant expiresAt) {
        SecretKey key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        JwtClaimsSet claims =
                JwtClaimsSet.builder()
                        .issuer(issuer)
                        .audience(List.of(audience))
                        .subject(UUID.randomUUID().toString())
                        .claim("email", "x@example.com")
                        .claim("role", "USER")
                        .issuedAt(expiresAt.minusSeconds(60))
                        .expiresAt(expiresAt)
                        .build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private static String mintHs512Token(String secret, String issuer) {
        // Pad the secret to satisfy HS512's 512-bit minimum (it accepts the same key bytes,
        // and the only purpose here is to produce a token with alg=HS512 in the header).
        byte[] padded = new byte[64];
        byte[] raw = secret.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(raw, 0, padded, 0, Math.min(raw.length, padded.length));
        SecretKey key = new SecretKeySpec(padded, "HmacSHA512");
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS512).build();
        Instant now = Instant.now();
        JwtClaimsSet claims =
                JwtClaimsSet.builder()
                        .issuer(issuer)
                        .subject(UUID.randomUUID().toString())
                        .issuedAt(now)
                        .expiresAt(now.plusSeconds(300))
                        .build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
