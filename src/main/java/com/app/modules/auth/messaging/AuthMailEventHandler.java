package com.app.modules.auth.messaging;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import com.app.common.messaging.exception.PermanentMessageException;
import com.app.common.outbox.model.DomainEventEnvelope;
import com.app.modules.auth.entity.User;
import com.app.modules.auth.enums.UserStatus;
import com.app.modules.auth.repository.UserRepository;
import com.app.modules.auth.service.TokenService;
import com.app.modules.mail.config.MailProperties;
import com.app.modules.mail.service.MailSender;

/** Handles auth-owned mail events delivered from RabbitMQ. */
@Component
public class AuthMailEventHandler {

    static final String AGGREGATE_TYPE_USER = "user";
    static final String USER_ID_DATA_KEY = "userId";

    private final UserRepository userRepository;
    private final TokenService tokenService;
    private final MailSender mailSender;
    private final MailProperties mailProperties;

    public AuthMailEventHandler(
            UserRepository userRepository,
            TokenService tokenService,
            MailSender mailSender,
            MailProperties mailProperties) {
        this.userRepository = userRepository;
        this.tokenService = tokenService;
        this.mailSender = mailSender;
        this.mailProperties = mailProperties;
    }

    public void handle(DomainEventEnvelope event, AuthMailEventProcessingContext context) {
        UUID userId = validateAndExtractUserId(event);
        User user =
                userRepository
                        .findByIdAndDeletedAtIsNull(userId)
                        .orElseThrow(() -> new PermanentMessageException("User not found"));
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new PermanentMessageException("User is not active");
        }

        switch (event.eventType()) {
            case AuthEventTypes.USER_REGISTERED_V1 ->
                    mailSender.sendWelcome(user.getEmail(), displayName(user));
            case AuthEventTypes.AUTH_EMAIL_VERIFICATION_REQUESTED_V1 -> {
                String token = context.emailVerificationToken(tokenService, userId);
                mailSender.sendEmailVerification(
                        user.getEmail(), displayName(user), buildVerifyEmailUrl(token));
            }
            case AuthEventTypes.AUTH_PASSWORD_RESET_REQUESTED_V1 -> {
                String token = context.passwordResetToken(tokenService, userId);
                mailSender.sendPasswordReset(
                        user.getEmail(), displayName(user), buildResetUrl(token));
            }
            case AuthEventTypes.AUTH_PASSWORD_CHANGED_V1 ->
                    mailSender.sendPasswordChanged(user.getEmail(), displayName(user));
            case AuthEventTypes.AUTH_OAUTH_ACCOUNT_NO_PASSWORD_V1 ->
                    mailSender.sendOAuthAccountNoPassword(user.getEmail(), displayName(user));
            default -> throw new PermanentMessageException("Unsupported auth mail event type");
        }
    }

    private UUID validateAndExtractUserId(DomainEventEnvelope event) {
        if (event == null) {
            throw new PermanentMessageException("Event envelope is null");
        }
        if (event.eventId() == null) {
            throw new PermanentMessageException("Event id is missing");
        }
        if (!AuthEventTypes.MAIL_EVENT_ROUTING_KEYS.contains(event.eventType())) {
            throw new PermanentMessageException("Unsupported auth mail event type");
        }
        if (!AGGREGATE_TYPE_USER.equals(event.aggregateType())) {
            throw new PermanentMessageException("Auth mail event aggregate type must be user");
        }
        if (event.aggregateId() == null) {
            throw new PermanentMessageException("Auth mail event aggregate id is missing");
        }
        UUID dataUserId = extractDataUserId(event.data());
        if (!event.aggregateId().equals(dataUserId)) {
            throw new PermanentMessageException(
                    "Auth mail event user id does not match aggregate id");
        }
        return dataUserId;
    }

    private UUID extractDataUserId(Map<String, Object> data) {
        if (data == null || !data.containsKey(USER_ID_DATA_KEY)) {
            throw new PermanentMessageException("Auth mail event data.userId is missing");
        }
        Object rawUserId = data.get(USER_ID_DATA_KEY);
        if (!(rawUserId instanceof String userIdValue) || userIdValue.isBlank()) {
            throw new PermanentMessageException("Auth mail event data.userId is invalid");
        }
        try {
            return UUID.fromString(userIdValue);
        } catch (IllegalArgumentException ex) {
            throw new PermanentMessageException("Auth mail event data.userId is not a UUID", ex);
        }
    }

    private String buildVerifyEmailUrl(String token) {
        return buildFrontendUrl(mailProperties.getVerifyEmailPath(), token);
    }

    private String buildResetUrl(String token) {
        return buildFrontendUrl(mailProperties.getResetPasswordPath(), token);
    }

    private String buildFrontendUrl(String path, String token) {
        URI baseUri = URI.create(mailProperties.getFrontendBaseUrl());
        return UriComponentsBuilder.fromUri(baseUri)
                .path(normalizedPath(path))
                .queryParam("token", token)
                .build()
                .toUriString();
    }

    private String normalizedPath(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        return path.startsWith("/") ? path : "/" + path;
    }

    private String displayName(User user) {
        if (user.getDisplayName() != null && !user.getDisplayName().isBlank()) {
            return user.getDisplayName();
        }
        return user.getUsername();
    }

    /** Per-message context that keeps generated raw tokens stable across in-memory retries. */
    public static final class AuthMailEventProcessingContext {

        private String emailVerificationToken;
        private String passwordResetToken;

        String emailVerificationToken(TokenService tokenService, UUID userId) {
            if (emailVerificationToken == null) {
                emailVerificationToken = tokenService.createEmailVerificationToken(userId);
            }
            return emailVerificationToken;
        }

        String passwordResetToken(TokenService tokenService, UUID userId) {
            if (passwordResetToken == null) {
                passwordResetToken = tokenService.createPasswordResetToken(userId);
            }
            return passwordResetToken;
        }
    }
}
