package com.app.modules.social.repository;

import java.util.UUID;

import com.app.modules.social.entity.Follow;
import com.app.modules.social.enums.FollowStatus;

public interface FollowRepositoryCustom {

    Follow insert(UUID followerId, UUID followingId, FollowStatus status);
}
