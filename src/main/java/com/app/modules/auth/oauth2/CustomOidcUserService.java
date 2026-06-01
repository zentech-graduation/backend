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
import com.app.modules.auth.entity.UserCredential;
import com.app.modules.auth.enums.OAuthProvider;
import com.app.modules.auth.repository.OAuthAccountRepository;
import com.app.modules.auth.repository.UserCredentialRepository;
import com.app.modules.auth.validation.UserStateValidator;
import com.app.modules.users.entity.User;
import com.app.modules.users.entity.UserSettings;
import com.app.modules.users.enums.UserRole;
import com.app.modules.users.enums.UserStatus;
import com.app.modules.users.repository.UserRepository;
import com.app.modules.users.repository.UserSettingsRepository;

/**
 * OIDC user service that resolves an OAuth2 sign-in to a local {@link User}.
 *
 * <p>OAuth2 login resolution:
 *
 * <ol>
 *   <li>The provider redirects the user to {@code /api/v1/auth/oauth2/callback/{registrationId}}.
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
    private final UserStateValidator userStateValidator;

    public CustomOidcUserService(
            OAuthAccountRepository oauthAccountRepository,
            UserRepository userRepository,
            UserCredentialRepository userCredentialRepository,
            UserSettingsRepository userSettingsRepository,
            UserStateValidator userStateValidator) {
        this.oauthAccountRepository = oauthAccountRepository;
        this.userRepository = userRepository;
        this.userCredentialRepository = userCredentialRepository;
        this.userSettingsRepository = userSettingsRepository;
        this.userStateValidator = userStateValidator;
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
        OAuthProvider provider = resolveProvider(request);
        String email = oidcUser.getEmail();
        String providerId = oidcUser.getSubject();
        String displayName = oidcUser.getFullName();
        String avatarUrl = oidcUser.getPicture();

        Optional<OAuthAccount> existing =
                oauthAccountRepository.findByProviderAndProviderId(provider, providerId);

        User user;
        if (existing.isPresent()) {
            user =
                    userRepository
                            .findByIdAndDeletedAtIsNull(existing.get().getUserId())
                            .orElseThrow(() -> new AppException(ApiErrorCode.NOT_FOUND));
            userStateValidator.enforceActive(user);
        } else {
            Optional<User> existingByEmail = userRepository.findByEmail(email);

            if (existingByEmail.isPresent()) {
                User found = existingByEmail.get();
                // A soft-deleted account retains its email (DB UNIQUE constraint is table-wide).
                // Silently creating a new account would hit the constraint; surface a clear error.
                if (found.getDeletedAt() != null) {
                    throw new AppException(ApiErrorCode.USER_EMAIL_ALREADY_EXISTS);
                }
                // Refuse to link an OAuth identity to a pre-existing local account unless the
                // IdP confirms the email is verified. Without this gate, a hostile or
                // misconfigured IdP could be used to take over any account by email.
                if (!Boolean.TRUE.equals(oidcUser.getEmailVerified())) {
                    throw new AppException(ApiErrorCode.AUTH_INVALID_CREDENTIALS);
                }
                user = found;
                userStateValidator.enforceActive(user);
            } else {
                user = createNewOAuthUser(email, displayName, avatarUrl);
            }

            OAuthAccount oauthAccount =
                    OAuthAccount.builder()
                            .userId(user.getId())
                            .provider(provider)
                            .providerId(providerId)
                            .providerEmail(email)
                            .build();
            oauthAccountRepository.save(oauthAccount);
        }

        return new CustomOidcUser(oidcUser, user);
    }

    /**
     * Maps OAuth2 registration IDs to the application's OAuthProvider enum. Add cases here when
     * wiring new providers; each new provider requires dedicated validation before being enabled.
     */
    // VisibleForTesting
    OAuthProvider resolveProvider(OidcUserRequest userRequest) {
        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        return switch (registrationId.toLowerCase()) {
            case "google" -> OAuthProvider.GOOGLE;
            // Additional providers can be added here as they are wired.
            default ->
                    throw new OAuth2AuthenticationException(
                            new OAuth2Error("unsupported_provider"),
                            "OAuth2 provider '" + registrationId + "' is not supported");
        };
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
        if (!userRepository.existsByUsername(base)) {
            return base;
        }
        for (int i = 2; i <= MAX_USERNAME_ATTEMPTS; i++) {
            String candidate = base + "_" + i;
            if (!userRepository.existsByUsername(candidate)) {
                return candidate;
            }
        }
        for (int i = 0; i < 5; i++) {
            String candidate = base + "_" + (1000 + ThreadLocalRandom.current().nextInt(9000));
            if (!userRepository.existsByUsername(candidate)) {
                return candidate;
            }
        }
        throw new AppException(ApiErrorCode.INTERNAL_ERROR);
    }
}
