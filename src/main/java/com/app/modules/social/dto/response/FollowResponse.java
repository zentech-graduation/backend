package com.app.modules.social.dto.response;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.app.modules.social.enums.FollowStatus;

/** API response returned after creating a follow relationship or follow request. */
public record FollowResponse(
        UUID followerId, UUID followingId, FollowStatus status, OffsetDateTime createdAt) {}
