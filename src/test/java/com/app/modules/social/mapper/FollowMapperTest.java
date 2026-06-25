package com.app.modules.social.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.app.modules.social.dto.response.FollowResponse;
import com.app.modules.social.entity.Follow;
import com.app.modules.social.entity.FollowId;
import com.app.modules.social.enums.FollowStatus;

class FollowMapperTest {

    private static final UUID FOLLOWER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID FOLLOWING_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000002");

    private FollowMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new FollowMapper();
    }

    @Test
    void toResponse_mapsAllFields() {
        OffsetDateTime createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        Follow follow =
                Follow.builder()
                        .id(new FollowId(FOLLOWER_ID, FOLLOWING_ID))
                        .status(FollowStatus.ACCEPTED)
                        .createdAt(createdAt)
                        .build();

        FollowResponse response = mapper.toResponse(follow);

        assertThat(response.followerId()).isEqualTo(FOLLOWER_ID);
        assertThat(response.followingId()).isEqualTo(FOLLOWING_ID);
        assertThat(response.status()).isEqualTo(FollowStatus.ACCEPTED);
        assertThat(response.createdAt()).isEqualTo(createdAt);
    }
}
