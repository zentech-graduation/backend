package com.app.modules.social.service.impl;

import static com.app.modules.social.messaging.SocialEventTypes.USER_FOLLOWED_V1;
import static com.app.modules.social.messaging.SocialEventTypes.USER_FOLLOW_REQUESTED_V1;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import com.app.common.messaging.RecommendationInteractionContract;
import com.app.common.outbox.service.OutboxService;
import com.app.modules.social.entity.Follow;
import com.app.modules.social.enums.FollowStatus;
import com.app.modules.social.service.SocialEventService;

@Service
public class SocialEventServiceImpl implements SocialEventService {

    private static final String AGGREGATE_TYPE_USER = "user";

    private final OutboxService outboxService;

    public SocialEventServiceImpl(OutboxService outboxService) {
        this.outboxService = outboxService;
    }

    @Override
    public void publishFollowCreated(Follow follow) {
        Assert.notNull(follow, "follow must not be null");
        Assert.notNull(follow.getId(), "follow.id must not be null");
        Assert.notNull(follow.getStatus(), "follow.status must not be null");

        UUID followerId = follow.getId().getFollowerId();
        UUID followingId = follow.getId().getFollowingId();
        Assert.notNull(followerId, "follow.followerId must not be null");
        Assert.notNull(followingId, "follow.followingId must not be null");

        String eventType = eventTypeFor(follow.getStatus());
        outboxService.enqueue(
                eventType,
                eventType,
                AGGREGATE_TYPE_USER,
                followingId,
                followerId,
                Map.of(
                        "followerId",
                        followerId.toString(),
                        "followingId",
                        followingId.toString(),
                        "status",
                        follow.getStatus().name().toLowerCase()));
    }

    @Override
    public void publishProfileFollowInteraction(UUID followerId, UUID followingId) {
        publishInteraction("profile_follow", followerId, followingId);
    }

    @Override
    public void publishProfileUnfollowInteraction(UUID followerId, UUID followingId) {
        publishInteraction("profile_unfollow", followerId, followingId);
    }

    private void publishInteraction(String eventType, UUID followerId, UUID followingId) {
        Map<String, Object> data = new HashMap<>();
        data.put("eventType", eventType);
        data.put("entityType", "user");
        data.put("entityId", followingId.toString());
        data.put("targetUserId", followingId.toString());
        outboxService.enqueue(
                RecommendationInteractionContract.REC_INTERACTION_RECORDED_V1,
                RecommendationInteractionContract.REC_INTERACTION_RECORDED_V1,
                AGGREGATE_TYPE_USER,
                followingId,
                followerId,
                data);
    }

    private static String eventTypeFor(FollowStatus status) {
        return switch (status) {
            case ACCEPTED -> USER_FOLLOWED_V1;
            case PENDING -> USER_FOLLOW_REQUESTED_V1;
        };
    }
}
