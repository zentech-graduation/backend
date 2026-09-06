package com.app.modules.message.repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.app.modules.message.entity.MessageWriteIdempotency;

@Repository
public interface MessageIdempotencyRepository extends JpaRepository<MessageWriteIdempotency, UUID> {

    Optional<MessageWriteIdempotency> findByUserIdAndIdempotencyKey(
            UUID userId, String idempotencyKey);

    /**
     * Atomically reserves an idempotency key without raising a constraint violation.
     *
     * <p>Uses {@code ON CONFLICT DO NOTHING} (the same pattern as {@code processed_messages}) so a
     * concurrent duplicate never poisons the caller's transaction. Returns 1 when this caller won
     * the reservation, 0 when the key already existed.
     *
     * @param userId owner of the key
     * @param key client-supplied idempotency key
     * @param requestHash hash of the reserving request's payload
     * @return 1 if reserved by this call, 0 if the key was already present
     */
    @Modifying
    @Query(
            value =
                    "INSERT INTO message_write_idempotency (user_id, idempotency_key, request_hash) "
                            + "VALUES (:userId, :key, :hash) "
                            + "ON CONFLICT (user_id, idempotency_key) DO NOTHING",
            nativeQuery = true)
    int insertIfAbsent(
            @Param("userId") UUID userId,
            @Param("key") String key,
            @Param("hash") String requestHash);

    /**
     * Stores the cached response body on a previously reserved idempotency row.
     *
     * @param userId owner of the key
     * @param key client-supplied idempotency key
     * @param body serialized response to replay on retry
     */
    @Modifying
    @Query(
            "UPDATE MessageWriteIdempotency e SET e.responseBody = :body "
                    + "WHERE e.userId = :userId AND e.idempotencyKey = :key")
    void updateResponseBody(
            @Param("userId") UUID userId, @Param("key") String key, @Param("body") String body);

    /**
     * Purges idempotency rows older than the cutoff so the table stays bounded.
     *
     * @param cutoff rows created before this instant are removed
     * @return number of rows deleted
     */
    @Modifying
    @Query("DELETE FROM MessageWriteIdempotency e WHERE e.createdAt < :cutoff")
    int deleteByCreatedAtBefore(@Param("cutoff") OffsetDateTime cutoff);
}
