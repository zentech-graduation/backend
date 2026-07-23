package com.app.modules.message.repository;

import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ConversationRepositoryImpl implements ConversationRepositoryCustom {

    // hashtextextended returns a bigint, matching pg_advisory_xact_lock(bigint); the same
    // LEAST/GREATEST expression is projected back out as "key" so the lock and the returned pair
    // key are always derived from one identical computation.
    private static final String LOCK_AND_KEY_SQL =
            """
			SELECT pg_advisory_xact_lock(hashtextextended(pair.key, 0)) AS locked, pair.key
			FROM (SELECT LEAST(:userA, :userB)::text || ':' || GREATEST(:userA, :userB)::text AS key) pair
			""";

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public ConversationRepositoryImpl(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public String lockDirectConversationPair(UUID userA, UUID userB) {
        MapSqlParameterSource params =
                new MapSqlParameterSource().addValue("userA", userA).addValue("userB", userB);
        return jdbcTemplate.queryForObject(
                LOCK_AND_KEY_SQL, params, (rs, rowNum) -> rs.getString("key"));
    }
}
