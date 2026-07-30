package com.app.modules.comment.live;

import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

import com.app.common.security.user.UserPrincipal;
import com.app.common.security.websocket.JwtHandshakeInterceptor;
import com.app.modules.post.entity.Post;
import com.app.modules.post.repository.PostRepository;
import com.app.modules.post.service.PostVisibilityService;

/**
 * Enforces per-post visibility on every STOMP SUBSCRIBE to a comment topic.
 *
 * <p>The destination encodes the post id ({@code /topic/comments.{postId}.*}); the subscription is
 * rejected when the handshake principal cannot see that post. Post visibility already covers block
 * and private-follow gating, so no separate block check is needed.
 */
@Component
@ConditionalOnProperty(prefix = "app.comment.live", name = "enabled", havingValue = "true")
public class CommentWebSocketAuthInterceptor implements ChannelInterceptor {

    private static final Pattern DESTINATION =
            Pattern.compile("/topic/comments\\.([0-9a-fA-F\\-]{36})\\..+");

    private final PostRepository postRepository;
    private final PostVisibilityService postVisibilityService;

    public CommentWebSocketAuthInterceptor(
            PostRepository postRepository, PostVisibilityService postVisibilityService) {
        this.postRepository = postRepository;
        this.postVisibilityService = postVisibilityService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
        if (!StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            return message;
        }
        String destination = accessor.getDestination();
        if (destination == null) {
            return message;
        }
        Matcher matcher = DESTINATION.matcher(destination);
        if (!matcher.matches()) {
            return message;
        }
        UUID postId = UUID.fromString(matcher.group(1));
        UUID viewerId = resolveViewer(accessor);
        Post post =
                postRepository
                        .findById(postId)
                        .orElseThrow(
                                () ->
                                        new MessageDeliveryException(
                                                message, "Post not found for subscription"));
        if (!postVisibilityService.isVisibleTo(viewerId, post)) {
            throw new MessageDeliveryException(message, "Subscription not permitted");
        }
        return message;
    }

    private UUID resolveViewer(StompHeaderAccessor accessor) {
        Map<String, Object> attributes = accessor.getSessionAttributes();
        Object principal =
                attributes == null
                        ? null
                        : attributes.get(JwtHandshakeInterceptor.PRINCIPAL_ATTRIBUTE);
        if (principal instanceof UserPrincipal userPrincipal) {
            return userPrincipal.userId();
        }
        return null;
    }
}
