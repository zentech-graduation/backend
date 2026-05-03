package com.app.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
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

    private final JwtProperties properties = new JwtProperties(SECRET, ISSUER, 900, 2592000);
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
        String expiredToken = mintTokenWithExpiry(SECRET, ISSUER, Instant.now().minusSeconds(60));

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
                        SECRET, "https://other-issuer.local", Instant.now().plusSeconds(60));

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

    private static String mintTokenWithExpiry(String secret, String issuer, Instant expiresAt) {
        SecretKey key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        JwtClaimsSet claims =
                JwtClaimsSet.builder()
                        .issuer(issuer)
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
