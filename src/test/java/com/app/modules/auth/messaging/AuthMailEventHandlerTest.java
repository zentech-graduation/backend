package com.app.modules.auth.messaging;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.app.common.messaging.exception.PermanentMessageException;
import com.app.common.outbox.model.DomainEventEnvelope;
import com.app.modules.auth.service.TokenService;
import com.app.modules.mail.config.MailProperties;
import com.app.modules.mail.service.MailSender;
import com.app.modules.users.entity.User;
import com.app.modules.users.enums.UserRole;
import com.app.modules.users.enums.UserStatus;
import com.app.modules.users.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class AuthMailEventHandlerTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000123");

    @Mock private UserRepository userRepository;
    @Mock private TokenService tokenService;
    @Mock private MailSender mailSender;

    private AuthMailEventHandler handler;

    @BeforeEach
    void setUp() {
        MailProperties properties = new MailProperties();
        properties.setFrontendBaseUrl("http://localhost:5173");
        properties.setVerifyEmailPath("/verify-email");
        properties.setResetPasswordPath("/reset-password");
        handler = new AuthMailEventHandler(userRepository, tokenService, mailSender, properties);
    }

    @Test
    void handleUserRegistered_sendsWelcomeMail() {
        when(userRepository.findByIdAndDeletedAtIsNull(USER_ID)).thenReturn(Optional.of(user()));

        handler.handle(
                event(AuthEventTypes.USER_REGISTERED_V1),
                new AuthMailEventHandler.AuthMailEventProcessingContext());

        verify(mailSender).sendWelcome("duc@example.com", "Ngoc Duc");
    }

    @Test
    void handleVerificationEvent_createsTokenAndSendsVerificationUrl() {
        when(userRepository.findByIdAndDeletedAtIsNull(USER_ID)).thenReturn(Optional.of(user()));
        when(tokenService.createEmailVerificationToken(USER_ID)).thenReturn("verify-token");

        handler.handle(
                event(AuthEventTypes.AUTH_EMAIL_VERIFICATION_REQUESTED_V1),
                new AuthMailEventHandler.AuthMailEventProcessingContext());

        verify(mailSender)
                .sendEmailVerification(
                        "duc@example.com",
                        "Ngoc Duc",
                        "http://localhost:5173/verify-email?token=verify-token");
    }

    @Test
    void handlePasswordReset_reusesGeneratedTokenWithinProcessingContext() {
        when(userRepository.findByIdAndDeletedAtIsNull(USER_ID)).thenReturn(Optional.of(user()));
        when(tokenService.createPasswordResetToken(USER_ID)).thenReturn("reset-token");
        AuthMailEventHandler.AuthMailEventProcessingContext context =
                new AuthMailEventHandler.AuthMailEventProcessingContext();
        DomainEventEnvelope event = event(AuthEventTypes.AUTH_PASSWORD_RESET_REQUESTED_V1);

        handler.handle(event, context);
        handler.handle(event, context);

        verify(tokenService).createPasswordResetToken(USER_ID);
        verify(mailSender, times(2))
                .sendPasswordReset(
                        "duc@example.com",
                        "Ngoc Duc",
                        "http://localhost:5173/reset-password?token=reset-token");
    }

    @Test
    void handleInvalidUserId_throwsPermanentFailureBeforeSendingMail() {
        DomainEventEnvelope event =
                new DomainEventEnvelope(
                        UUID.randomUUID(),
                        AuthEventTypes.USER_REGISTERED_V1,
                        OffsetDateTime.now(ZoneOffset.UTC),
                        USER_ID,
                        "user",
                        USER_ID,
                        Map.of("userId", UUID.randomUUID().toString()));

        assertThatThrownBy(
                        () ->
                                handler.handle(
                                        event,
                                        new AuthMailEventHandler.AuthMailEventProcessingContext()))
                .isInstanceOf(PermanentMessageException.class);
        verify(mailSender, never()).sendWelcome(contains("@"), contains("Duc"));
    }

    @Test
    void handleMissingUser_throwsPermanentFailure() {
        when(userRepository.findByIdAndDeletedAtIsNull(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(
                        () ->
                                handler.handle(
                                        event(AuthEventTypes.USER_REGISTERED_V1),
                                        new AuthMailEventHandler.AuthMailEventProcessingContext()))
                .isInstanceOf(PermanentMessageException.class);
    }

    @Test
    void handle_nullEventId_throwsPermanentFailure() {
        DomainEventEnvelope envelope =
                new DomainEventEnvelope(
                        null,
                        AuthEventTypes.USER_REGISTERED_V1,
                        OffsetDateTime.now(ZoneOffset.UTC),
                        USER_ID,
                        "user",
                        USER_ID,
                        Map.of("userId", USER_ID.toString()));

        assertThatThrownBy(
                        () ->
                                handler.handle(
                                        envelope,
                                        new AuthMailEventHandler.AuthMailEventProcessingContext()))
                .isInstanceOf(PermanentMessageException.class)
                .hasMessageContaining("missing");
    }

    @Test
    void handle_unsupportedEventType_throwsPermanentFailure() {
        DomainEventEnvelope envelope =
                new DomainEventEnvelope(
                        UUID.randomUUID(),
                        "unknown.event.type",
                        OffsetDateTime.now(ZoneOffset.UTC),
                        USER_ID,
                        "user",
                        USER_ID,
                        Map.of("userId", USER_ID.toString()));

        assertThatThrownBy(
                        () ->
                                handler.handle(
                                        envelope,
                                        new AuthMailEventHandler.AuthMailEventProcessingContext()))
                .isInstanceOf(PermanentMessageException.class)
                .hasMessageContaining("Unsupported");
    }

    @Test
    void handle_wrongAggregateType_throwsPermanentFailure() {
        DomainEventEnvelope envelope =
                new DomainEventEnvelope(
                        UUID.randomUUID(),
                        AuthEventTypes.USER_REGISTERED_V1,
                        OffsetDateTime.now(ZoneOffset.UTC),
                        USER_ID,
                        "post",
                        USER_ID,
                        Map.of("userId", USER_ID.toString()));

        assertThatThrownBy(
                        () ->
                                handler.handle(
                                        envelope,
                                        new AuthMailEventHandler.AuthMailEventProcessingContext()))
                .isInstanceOf(PermanentMessageException.class)
                .hasMessageContaining("aggregate type");
    }

    @Test
    void handle_nullAggregateId_throwsPermanentFailure() {
        DomainEventEnvelope envelope =
                new DomainEventEnvelope(
                        UUID.randomUUID(),
                        AuthEventTypes.USER_REGISTERED_V1,
                        OffsetDateTime.now(ZoneOffset.UTC),
                        USER_ID,
                        "user",
                        null,
                        Map.of("userId", USER_ID.toString()));

        assertThatThrownBy(
                        () ->
                                handler.handle(
                                        envelope,
                                        new AuthMailEventHandler.AuthMailEventProcessingContext()))
                .isInstanceOf(PermanentMessageException.class)
                .hasMessageContaining("aggregate id");
    }

    @Test
    void handle_nullData_throwsPermanentFailure() {
        DomainEventEnvelope envelope =
                new DomainEventEnvelope(
                        UUID.randomUUID(),
                        AuthEventTypes.USER_REGISTERED_V1,
                        OffsetDateTime.now(ZoneOffset.UTC),
                        USER_ID,
                        "user",
                        USER_ID,
                        null);

        assertThatThrownBy(
                        () ->
                                handler.handle(
                                        envelope,
                                        new AuthMailEventHandler.AuthMailEventProcessingContext()))
                .isInstanceOf(PermanentMessageException.class)
                .hasMessageContaining("data.userId");
    }

    @Test
    void handle_missingUserIdKey_throwsPermanentFailure() {
        DomainEventEnvelope envelope =
                new DomainEventEnvelope(
                        UUID.randomUUID(),
                        AuthEventTypes.USER_REGISTERED_V1,
                        OffsetDateTime.now(ZoneOffset.UTC),
                        USER_ID,
                        "user",
                        USER_ID,
                        Map.of("otherId", "something"));

        assertThatThrownBy(
                        () ->
                                handler.handle(
                                        envelope,
                                        new AuthMailEventHandler.AuthMailEventProcessingContext()))
                .isInstanceOf(PermanentMessageException.class)
                .hasMessageContaining("data.userId");
    }

    @Test
    void handle_nonStringUserId_throwsPermanentFailure() {
        DomainEventEnvelope envelope =
                new DomainEventEnvelope(
                        UUID.randomUUID(),
                        AuthEventTypes.USER_REGISTERED_V1,
                        OffsetDateTime.now(ZoneOffset.UTC),
                        USER_ID,
                        "user",
                        USER_ID,
                        Map.<String, Object>of("userId", 42));

        assertThatThrownBy(
                        () ->
                                handler.handle(
                                        envelope,
                                        new AuthMailEventHandler.AuthMailEventProcessingContext()))
                .isInstanceOf(PermanentMessageException.class)
                .hasMessageContaining("invalid");
    }

    @Test
    void handle_nonUuidStringUserId_throwsPermanentFailure() {
        DomainEventEnvelope envelope =
                new DomainEventEnvelope(
                        UUID.randomUUID(),
                        AuthEventTypes.USER_REGISTERED_V1,
                        OffsetDateTime.now(ZoneOffset.UTC),
                        USER_ID,
                        "user",
                        USER_ID,
                        Map.of("userId", "not-a-uuid"));

        assertThatThrownBy(
                        () ->
                                handler.handle(
                                        envelope,
                                        new AuthMailEventHandler.AuthMailEventProcessingContext()))
                .isInstanceOf(PermanentMessageException.class)
                .hasMessageContaining("UUID");
    }

    @Test
    void handle_inactiveUser_throwsPermanentFailure() {
        User suspended =
                User.builder()
                        .id(USER_ID)
                        .username("duc")
                        .email("duc@example.com")
                        .displayName("Ngoc Duc")
                        .role(UserRole.USER)
                        .status(UserStatus.SUSPENDED)
                        .build();
        when(userRepository.findByIdAndDeletedAtIsNull(USER_ID)).thenReturn(Optional.of(suspended));

        assertThatThrownBy(
                        () ->
                                handler.handle(
                                        event(AuthEventTypes.USER_REGISTERED_V1),
                                        new AuthMailEventHandler.AuthMailEventProcessingContext()))
                .isInstanceOf(PermanentMessageException.class)
                .hasMessageContaining("not active");
    }

    @Test
    void handle_displayNameNullFallsBackToUsername() {
        User noDisplayName =
                User.builder()
                        .id(USER_ID)
                        .username("duc")
                        .email("duc@example.com")
                        .displayName(null)
                        .role(UserRole.USER)
                        .status(UserStatus.ACTIVE)
                        .build();
        when(userRepository.findByIdAndDeletedAtIsNull(USER_ID))
                .thenReturn(Optional.of(noDisplayName));

        handler.handle(
                event(AuthEventTypes.USER_REGISTERED_V1),
                new AuthMailEventHandler.AuthMailEventProcessingContext());

        verify(mailSender).sendWelcome("duc@example.com", "duc");
    }

    @Test
    void handle_passwordChanged_sendsPasswordChangedMail() {
        when(userRepository.findByIdAndDeletedAtIsNull(USER_ID)).thenReturn(Optional.of(user()));

        handler.handle(
                event(AuthEventTypes.AUTH_PASSWORD_CHANGED_V1),
                new AuthMailEventHandler.AuthMailEventProcessingContext());

        verify(mailSender).sendPasswordChanged("duc@example.com", "Ngoc Duc");
    }

    @Test
    void handle_oauthAccountNoPassword_sendsOAuthMail() {
        when(userRepository.findByIdAndDeletedAtIsNull(USER_ID)).thenReturn(Optional.of(user()));

        handler.handle(
                event(AuthEventTypes.AUTH_OAUTH_ACCOUNT_NO_PASSWORD_V1),
                new AuthMailEventHandler.AuthMailEventProcessingContext());

        verify(mailSender).sendOAuthAccountNoPassword("duc@example.com", "Ngoc Duc");
    }

    private DomainEventEnvelope event(String eventType) {
        return new DomainEventEnvelope(
                UUID.randomUUID(),
                eventType,
                OffsetDateTime.now(ZoneOffset.UTC),
                USER_ID,
                "user",
                USER_ID,
                Map.of("userId", USER_ID.toString()));
    }

    private User user() {
        return User.builder()
                .id(USER_ID)
                .username("duc")
                .email("duc@example.com")
                .displayName("Ngoc Duc")
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();
    }
}
