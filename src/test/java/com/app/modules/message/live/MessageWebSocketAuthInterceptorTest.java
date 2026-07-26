package com.app.modules.message.live;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import com.app.common.security.user.UserPrincipal;
import com.app.modules.message.repository.ConversationParticipantRepository;
import com.app.modules.message.repository.MessageUserRepository;
import com.app.modules.users.entity.User;
import com.app.modules.users.enums.UserStatus;

@ExtendWith(MockitoExtension.class)
class MessageWebSocketAuthInterceptorTest {

    @Mock private ConversationParticipantRepository participantRepository;
    @Mock private MessageUserRepository userRepository;
    @Mock private MessageChannel channel;

    private MessageWebSocketAuthInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new MessageWebSocketAuthInterceptor(participantRepository, userRepository);
    }

    @Test
    void preSend_activeParticipantSubscribing_passesThroughUnchanged() {
        UUID conversationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        stubActiveAccount(userId);
        when(participantRepository.existsByIdConversationIdAndIdUserIdAndLeftAtIsNull(
                        conversationId, userId))
                .thenReturn(true);
        Message<?> message = subscribeMessage(conversationId, userId);

        Message<?> result = interceptor.preSend(message, channel);

        assertThat(result).isSameAs(message);
    }

    @Test
    void preSend_nonParticipantSubscribing_throwsMessageDeliveryException() {
        UUID conversationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        stubActiveAccount(userId);
        when(participantRepository.existsByIdConversationIdAndIdUserIdAndLeftAtIsNull(
                        conversationId, userId))
                .thenReturn(false);
        Message<?> message = subscribeMessage(conversationId, userId);

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void preSend_leftParticipantSubscribing_throwsMessageDeliveryException() {
        UUID conversationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        stubActiveAccount(userId);
        // A participant who left is not "active"; the existence check (which filters on
        // left_at IS NULL) correctly returns false for them, same as a stranger.
        when(participantRepository.existsByIdConversationIdAndIdUserIdAndLeftAtIsNull(
                        conversationId, userId))
                .thenReturn(false);
        Message<?> message = subscribeMessage(conversationId, userId);

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void preSend_softDeletedOrBannedAccountSubscribing_throwsMessageDeliveryException() {
        UUID conversationId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        // The account no longer being active/live must reject the SUBSCRIBE even though the
        // participant row itself is still active - this is the gap a stale but still-open
        // WebSocket session (from before the account was banned/deleted) would otherwise exploit.
        when(userRepository.findByIdAndDeletedAtIsNullAndStatus(userId, UserStatus.ACTIVE))
                .thenReturn(Optional.empty());
        Message<?> message = subscribeMessage(conversationId, userId);

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(MessageDeliveryException.class);
        verify(participantRepository, never())
                .existsByIdConversationIdAndIdUserIdAndLeftAtIsNull(any(), any());
    }

    @Test
    void preSend_nonSubscribeCommand_passesThroughWithoutChecking() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination("/topic/conversations." + UUID.randomUUID() + ".messages");
        accessor.setSessionAttributes(new HashMap<>());
        Message<?> message =
                MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, channel);

        assertThat(result).isSameAs(message);
        verify(participantRepository, never())
                .existsByIdConversationIdAndIdUserIdAndLeftAtIsNull(any(), any());
        verify(userRepository, never()).findByIdAndDeletedAtIsNullAndStatus(any(), any());
    }

    @Test
    void preSend_foreignDestination_passesThroughWithoutChecking() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/comments." + UUID.randomUUID() + ".events");
        accessor.setSessionAttributes(sessionAttributesFor(UUID.randomUUID()));
        Message<?> message =
                MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        Message<?> result = interceptor.preSend(message, channel);

        assertThat(result).isSameAs(message);
        verify(participantRepository, never())
                .existsByIdConversationIdAndIdUserIdAndLeftAtIsNull(any(), any());
        verify(userRepository, never()).findByIdAndDeletedAtIsNullAndStatus(any(), any());
    }

    @Test
    void preSend_nullPrincipal_throwsMessageDeliveryException() {
        UUID conversationId = UUID.randomUUID();
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/conversations." + conversationId + ".messages");
        accessor.setSessionAttributes(new HashMap<>());
        Message<?> message =
                MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        assertThatThrownBy(() -> interceptor.preSend(message, channel))
                .isInstanceOf(MessageDeliveryException.class);
        verify(participantRepository, never())
                .existsByIdConversationIdAndIdUserIdAndLeftAtIsNull(any(), any());
        verify(userRepository, never()).findByIdAndDeletedAtIsNullAndStatus(any(), any());
    }

    private void stubActiveAccount(UUID userId) {
        when(userRepository.findByIdAndDeletedAtIsNullAndStatus(userId, UserStatus.ACTIVE))
                .thenReturn(
                        Optional.of(User.builder().id(userId).status(UserStatus.ACTIVE).build()));
    }

    private static Message<?> subscribeMessage(UUID conversationId, UUID userId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/conversations." + conversationId + ".messages");
        accessor.setSessionAttributes(sessionAttributesFor(userId));
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private static Map<String, Object> sessionAttributesFor(UUID userId) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(
                MessageWebSocketJwtHandshakeInterceptor.PRINCIPAL_ATTRIBUTE,
                new UserPrincipal(userId, null, "user", "ACTIVE"));
        return attributes;
    }
}
