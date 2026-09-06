package com.app.common.security.jwt;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

import com.app.common.enums.ApiErrorCode;
import com.app.common.exception.AppException;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

/**
 * Issues and validates HS256-signed JWT access tokens using Spring Security's Nimbus integration.
 *
 * <p>The signing key is derived from {@code app.jwt.secret} and is symmetric: the same key encodes
 * and decodes. Refresh tokens are NOT JWTs and are handled separately by {@code
 * RefreshTokenService}.
 */
@Component
public class JwtTokenProvider {

    private final JwtProperties properties;
    private final JwtEncoder encoder;
    private final JwtDecoder decoder;

    public JwtTokenProvider(JwtProperties properties) {
        this.properties = properties;
        SecretKey key =
                new SecretKeySpec(
                        properties.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        NimbusJwtDecoder nimbusDecoder =
                NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        nimbusDecoder.setJwtValidator(
                new DelegatingOAuth2TokenValidator<>(
                        new JwtTimestampValidator(),
                        new JwtClaimValidator<String>(
                                JwtClaimNames.ISS, iss -> properties.issuer().equals(iss)),
                        new JwtClaimValidator<List<String>>(
                                JwtClaimNames.AUD,
                                aud -> aud != null && aud.contains(properties.audience()))));
        this.decoder = nimbusDecoder;
    }

    /**
     * Mints a new access token for the given identity.
     *
     * <p>The payload intentionally excludes email and other PII: a JWT payload is only Base64URL
     * encoded, not encrypted, and any holder of the token can read it. Downstream code resolves the
     * user (and email, when needed) from the {@code sub} claim server-side.
     *
     * @param userId stable user identifier; placed in the {@code sub} claim
     * @param role user role name; placed in a custom {@code role} claim
     * @param tokenEpoch the account's current {@code users.token_epoch}; placed in a custom {@code
     *     epoch} claim so an administrator can invalidate this token by advancing the column
     * @return the encoded JWT string
     */
    public String generateAccessToken(UUID userId, String role, int tokenEpoch) {
        Instant now = Instant.now();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        JwtClaimsSet claims =
                JwtClaimsSet.builder()
                        .issuer(properties.issuer())
                        .audience(List.of(properties.audience()))
                        .subject(userId.toString())
                        .claim("role", role)
                        .claim("jti", UUID.randomUUID().toString())
                        .claim("epoch", tokenEpoch)
                        .issuedAt(now)
                        .notBefore(now)
                        .expiresAt(now.plusSeconds(properties.accessTokenTtl()))
                        .build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    /**
     * Validates the signature, issuer, and expiry of the supplied token and returns the parsed
     * claims.
     *
     * @param token raw JWT string supplied by the caller
     * @return parsed claims for application use
     * @throws AppException with {@link ApiErrorCode#AUTH_TOKEN_EXPIRED} if expired, or {@link
     *     ApiErrorCode#AUTH_TOKEN_INVALID} for any other validation failure
     */
    public JwtClaims validateAndParse(String token) {
        Jwt jwt;
        try {
            jwt = decoder.decode(token);
        } catch (JwtValidationException ex) {
            if (ex.getMessage() != null && ex.getMessage().toLowerCase().contains("expired")) {
                throw new AppException(ApiErrorCode.AUTH_TOKEN_EXPIRED);
            }
            throw new AppException(ApiErrorCode.AUTH_TOKEN_INVALID);
        } catch (JwtException ex) {
            throw new AppException(ApiErrorCode.AUTH_TOKEN_INVALID);
        }

        Instant expiresAt = jwt.getExpiresAt();
        if (expiresAt != null && expiresAt.isBefore(Instant.now())) {
            throw new AppException(ApiErrorCode.AUTH_TOKEN_EXPIRED);
        }

        String subject = jwt.getSubject();
        if (subject == null) {
            throw new AppException(ApiErrorCode.AUTH_TOKEN_INVALID);
        }
        UUID userId;
        try {
            userId = UUID.fromString(subject);
        } catch (IllegalArgumentException ex) {
            throw new AppException(ApiErrorCode.AUTH_TOKEN_INVALID);
        }

        String role = jwt.getClaimAsString("role");
        String jti = jwt.getClaimAsString("jti");
        // Null for a token minted before the epoch claim existed. Left null here rather than
        // defaulted, so the resolver owns the decision to read a missing claim as epoch 0.
        Number epoch = jwt.getClaim("epoch");
        return new JwtClaims(userId, role, jti, epoch == null ? null : epoch.intValue(), expiresAt);
    }
}
