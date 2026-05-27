package com.app.modules.auth.service.impl;

import static com.app.modules.auth.messaging.AuthEventTypes.AUTH_EMAIL_VERIFICATION_REQUESTED_V1;
import static com.app.modules.auth.messaging.AuthEventTypes.AUTH_OAUTH_ACCOUNT_NO_PASSWORD_V1;
import static com.app.modules.auth.messaging.AuthEventTypes.AUTH_PASSWORD_CHANGED_V1;
import static com.app.modules.auth.messaging.AuthEventTypes.AUTH_PASSWORD_RESET_REQUESTED_V1;
import static com.app.modules.auth.messaging.AuthEventTypes.USER_REGISTERED_V1;

import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import com.app.common.outbox.service.OutboxService;
import com.app.modules.auth.service.AuthMailEventService;
import com.app.modules.users.entity.User;

@Service
public class AuthMailEventServiceImpl implements AuthMailEventService {

    private static final String AGGREGATE_TYPE_USER = "user";
    private static final String USER_ID_DATA_KEY = "userId";

    private final OutboxService outboxService;

    public AuthMailEventServiceImpl(OutboxService outboxService) {
        this.outboxService = outboxService;
    }

    @Override
    public void publishUserRegistered(User user) {
        enqueueUserEvent(user, USER_REGISTERED_V1);
    }

    @Override
    public void publishEmailVerificationRequested(User user, UUID actorId) {
        enqueueUserEvent(user, AUTH_EMAIL_VERIFICATION_REQUESTED_V1, actorId);
    }

    @Override
    public void publishPasswordResetRequested(User user, UUID actorId) {
        enqueueUserEvent(user, AUTH_PASSWORD_RESET_REQUESTED_V1, actorId);
    }

    @Override
    public void publishPasswordChanged(User user) {
        enqueueUserEvent(user, AUTH_PASSWORD_CHANGED_V1);
    }

    @Override
    public void publishOAuthAccountNoPassword(User user, UUID actorId) {
        enqueueUserEvent(user, AUTH_OAUTH_ACCOUNT_NO_PASSWORD_V1, actorId);
    }

    private void enqueueUserEvent(User user, String eventType) {
        enqueueUserEvent(user, eventType, user == null ? null : user.getId());
    }

    private void enqueueUserEvent(User user, String eventType, UUID actorId) {
        Assert.notNull(user, "user must not be null");
        UUID userId = user.getId();
        Assert.notNull(userId, "user.id must not be null");

        outboxService.enqueue(
                eventType,
                eventType,
                AGGREGATE_TYPE_USER,
                userId,
                actorId,
                Map.of(USER_ID_DATA_KEY, userId.toString()));
    }
}
