package com.app.modules.auth.oauth2;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.app.common.enums.ApiErrorCode;
import com.app.common.exception.AppException;
import com.app.modules.auth.entity.OAuthAccount;
import com.app.modules.auth.entity.User;
import com.app.modules.auth.entity.UserCredential;
import com.app.modules.auth.entity.UserSettings;
import com.app.modules.auth.enums.OAuthProvider;
import com.app.modules.auth.enums.UserRole;
import com.app.modules.auth.enums.UserStatus;
import com.app.modules.auth.repository.OAuthAccountRepository;
import com.app.modules.auth.repository.UserCredentialRepository;
import com.app.modules.auth.repository.UserRepository;
import com.app.modules.auth.repository.UserSettingsRepository;

/**
 * OIDC user service that resolves a Google sign-in to a local {@link User}.
 *
 * <p>OAuth2 login resolution:
 *
 * <ol>
 *   <li>Google redirects the user to {@code /api/v1/auth/oauth2/callback/google}.
 *   <li>Spring Security exchanges the auth code for tokens, then invokes {@link
 *       #loadUser(OidcUserRequest)}.
 *   <li>If an {@code oauth_accounts} row exists for the provider id, the linked user is loaded.
 *   <li>Otherwise, if the OIDC email matches an existing local account, a new OAuth link is added
 *       to that account.
 *   <li>Otherwise a new local user is created with {@code email_verified=true} and no password.
 *   <li>{@code OAuth2AuthenticationSuccessHandler} then issues a JWT access + refresh pair.
 * </ol>
 */
@Service
public class CustomOidcUserService extends OidcUserService {

    private static final int MAX_USERNAME_ATTEMPTS = 10;

    private final OAuthAccountRepository oauthAccountRepository;
    private final UserRepository userRepository;
    private final UserCredentialRepository userCredentialRepository;
    private final UserSettingsRepository userSettingsRepository;

    public CustomOidcUserService(
            OAuthAccountRepository oauthAccountRepository,
            UserRepository userRepository,
            UserCredentialRepository userCredentialRepository,
            UserSettingsRepository userSettingsRepository) {
        this.oauthAccountRepository = oauthAccountRepository;
        this.userRepository = userRepository;
        this.userCredentialRepository = userCredentialRepository;
        this.userSettingsRepository = userSettingsRepository;
    }

    @Override
    @Transactional
    public OidcUser loadUser(OidcUserRequest userRequest) throws OAuth2AuthenticationException {
        OidcUser oidcUser = super.loadUser(userRequest);
        try {
            return processOidcUser(userRequest, oidcUser);
        } catch (AppException ex) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("processing_error", ex.getMessage(), null), ex);
        }
    }

    private OidcUser processOidcUser(OidcUserRequest request, OidcUser oidcUser) {
        String email = oidcUser.getEmail();
        String providerId = oidcUser.getSubject();
        String displayName = oidcUser.getFullName();
        String avatarUrl = oidcUser.getPicture();

        Optional<OAuthAccount> existing =
                oauthAccountRepository.findByProviderAndProviderId(
                        OAuthProvider.GOOGLE, providerId);

        User user;
        if (existing.isPresent()) {
            user =
                    userRepository
                            .findByIdAndDeletedAtIsNull(existing.get().getUserId())
                            .orElseThrow(() -> new AppException(ApiErrorCode.NOT_FOUND));
            enforceStatus(user);
        } else {
            Optional<User> existingByEmail = userRepository.findByEmailAndDeletedAtIsNull(email);

            if (existingByEmail.isPresent()) {
                // Refuse to link an OAuth identity to a pre-existing local account unless the
                // IdP confirms the email is verified. Without this gate, a hostile or
                // misconfigured IdP could be used to take over any account by email.
                if (!Boolean.TRUE.equals(oidcUser.getEmailVerified())) {
                    throw new AppException(ApiErrorCode.AUTH_INVALID_CREDENTIALS);
                }
                user = existingByEmail.get();
                enforceStatus(user);
            } else {
                user = createNewOAuthUser(email, displayName, avatarUrl);
            }

            // Access token from the IdP is not consumed by any downstream call. Retaining it
            // would only widen the database-compromise blast radius.
            OAuthAccount oauthAccount =
                    OAuthAccount.builder()
                            .userId(user.getId())
                            .provider(OAuthProvider.GOOGLE)
                            .providerId(providerId)
                            .providerEmail(email)
                            .accessToken(null)
                            .build();
            oauthAccountRepository.save(oauthAccount);
        }

        return new CustomOidcUser(oidcUser, user);
    }

    private User createNewOAuthUser(String email, String displayName, String avatarUrl) {
        String baseUsername = email.split("@")[0].replaceAll("[^a-zA-Z0-9_.]", "_");
        // Username column is at most 30 characters; truncate the local-part before suffixing.
        if (baseUsername.length() > 24) {
            baseUsername = baseUsername.substring(0, 24);
        }
        String username = resolveUniqueUsername(baseUsername);

        User user =
                User.builder()
                        .username(username)
                        .email(email)
                        .displayName(displayName)
                        .avatarUrl(avatarUrl)
                        .role(UserRole.USER)
                        .status(UserStatus.ACTIVE)
                        .isPrivate(false)
                        .isVerified(false)
                        .build();
        user = userRepository.save(user);

        UserCredential cred =
                UserCredential.builder()
                        .userId(user.getId())
                        .passwordHash(null)
                        .emailVerified(true)
                        .emailVerifiedAt(OffsetDateTime.now())
                        .build();
        userCredentialRepository.save(cred);

        userSettingsRepository.save(UserSettings.builder().userId(user.getId()).build());
        return user;
    }

    private String resolveUniqueUsername(String base) {
        if (!userRepository.existsByUsernameAndDeletedAtIsNull(base)) {
            return base;
        }
        for (int i = 2; i <= MAX_USERNAME_ATTEMPTS; i++) {
            String candidate = base + "_" + i;
            if (!userRepository.existsByUsernameAndDeletedAtIsNull(candidate)) {
                return candidate;
            }
        }
        for (int i = 0; i < 5; i++) {
            String candidate = base + "_" + (1000 + ThreadLocalRandom.current().nextInt(9000));
            if (!userRepository.existsByUsernameAndDeletedAtIsNull(candidate)) {
                return candidate;
            }
        }
        throw new AppException(ApiErrorCode.INTERNAL_ERROR);
    }

    private void enforceStatus(User user) {
        switch (user.getStatus()) {
            case BANNED -> throw new AppException(ApiErrorCode.AUTH_ACCOUNT_LOCKED);
            case SUSPENDED, DEACTIVATED ->
                    throw new AppException(ApiErrorCode.AUTH_ACCOUNT_INACTIVE);
            default -> {}
        }
    }
}
