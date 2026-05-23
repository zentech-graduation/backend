package com.app.modules.auth.service.impl;

import static com.app.modules.auth.messaging.AuthEventTypes.AUTH_EMAIL_VERIFICATION_REQUESTED_V1;
import static com.app.modules.auth.messaging.AuthEventTypes.AUTH_OAUTH_ACCOUNT_NO_PASSWORD_V1;
import static com.app.modules.auth.messaging.AuthEventTypes.AUTH_PASSWORD_CHANGED_V1;
import static com.app.modules.auth.messaging.AuthEventTypes.AUTH_PASSWORD_RESET_REQUESTED_V1;
import static com.app.modules.auth.messaging.AuthEventTypes.USER_REGISTERED_V1;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.app.common.outbox.service.OutboxService;
import com.app.modules.auth.entity.User;

@ExtendWith(MockitoExtension.class)
class AuthMailEventServiceImplTest {

    @Mock private OutboxService outboxService;

    @Test
    void publishMethods_enqueueMatchingRoutingKeysActorsAndMinimalPayload() {
        AuthMailEventServiceImpl service = new AuthMailEventServiceImpl(outboxService);
        User user = User.builder().id(UUID.randomUUID()).build();

        service.publishUserRegistered(user);
        service.publishEmailVerificationRequested(user, null);
        service.publishPasswordResetRequested(user, null);
        service.publishPasswordChanged(user);
        service.publishOAuthAccountNoPassword(user, null);

        ArgumentCaptor<String> eventTypeCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> routingKeyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> aggregateTypeCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<UUID> aggregateIdCaptor = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<UUID> actorIdCaptor = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<Map<String, Object>> dataCaptor = ArgumentCaptor.forClass(Map.class);
        verify(outboxService, times(5))
                .enqueue(
                        eventTypeCaptor.capture(),
                        routingKeyCaptor.capture(),
                        aggregateTypeCaptor.capture(),
                        aggregateIdCaptor.capture(),
                        actorIdCaptor.capture(),
                        dataCaptor.capture());

        assertThat(eventTypeCaptor.getAllValues()).containsExactlyElementsOf(expectedEventTypes());
        assertThat(routingKeyCaptor.getAllValues()).containsExactlyElementsOf(expectedEventTypes());
        assertThat(aggregateTypeCaptor.getAllValues()).containsOnly("user");
        assertThat(aggregateIdCaptor.getAllValues()).containsOnly(user.getId());
        assertThat(actorIdCaptor.getAllValues())
                .containsExactly(user.getId(), null, null, user.getId(), null);
        assertThat(dataCaptor.getAllValues())
                .allSatisfy(
                        data ->
                                assertThat(data)
                                        .containsExactly(
                                                Map.entry("userId", user.getId().toString())));
    }

    private static List<String> expectedEventTypes() {
        return List.of(
                USER_REGISTERED_V1,
                AUTH_EMAIL_VERIFICATION_REQUESTED_V1,
                AUTH_PASSWORD_RESET_REQUESTED_V1,
                AUTH_PASSWORD_CHANGED_V1,
                AUTH_OAUTH_ACCOUNT_NO_PASSWORD_V1);
    }
}
