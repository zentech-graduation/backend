package com.app.modules.social.repository;

import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class BlockRepository {

    private final JdbcClient jdbcClient;

    public BlockRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public boolean existsBetween(UUID firstUserId, UUID secondUserId) {
        return Boolean.TRUE.equals(
                jdbcClient
                        .sql(
                                """
								SELECT EXISTS (
									SELECT 1
									FROM blocks
									WHERE (blocker_id = :firstUserId AND blocked_id = :secondUserId)
									OR (blocker_id = :secondUserId AND blocked_id = :firstUserId)
								)
								""")
                        .param("firstUserId", firstUserId)
                        .param("secondUserId", secondUserId)
                        .query(Boolean.class)
                        .single());
    }
}
