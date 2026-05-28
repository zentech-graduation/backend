package com.app.modules.social.mapper;

import org.springframework.stereotype.Component;

import com.app.modules.social.dto.response.FollowResponse;
import com.app.modules.social.entity.Follow;

@Component
public class FollowMapper {

    public FollowResponse toResponse(Follow follow) {
        return new FollowResponse(
                follow.getId().getFollowerId(),
                follow.getId().getFollowingId(),
                follow.getStatus(),
                follow.getCreatedAt());
    }
}
