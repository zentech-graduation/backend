package com.app.modules.social.repository;

import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;

import com.app.modules.social.entity.Follow;
import com.app.modules.social.entity.FollowId;
import com.app.modules.social.enums.FollowStatus;

public class FollowRepositoryImpl implements FollowRepositoryCustom {

    private final JdbcClient jdbcClient;

    public FollowRepositoryImpl(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Follow insert(UUID followerId, UUID followingId, FollowStatus status) {
        return jdbcClient
                .sql(
                        """
						INSERT INTO follows(follower_id, following_id, status)
						VALUES (:followerId, :followingId, CAST(:status AS follow_status))
						RETURNING follower_id, following_id, status, created_at
						""")
                .param("followerId", followerId)
                .param("followingId", followingId)
                .param("status", status.name().toLowerCase())
                .query(
                        (rs, rowNum) ->
                                Follow.builder()
                                        .id(
                                                new FollowId(
                                                        rs.getObject("follower_id", UUID.class),
                                                        rs.getObject("following_id", UUID.class)))
                                        .status(
                                                FollowStatus.valueOf(
                                                        rs.getString("status").toUpperCase()))
                                        .createdAt(
                                                rs.getObject(
                                                        "created_at",
                                                        java.time.OffsetDateTime.class))
                                        .build())
                .single();
    }
}
