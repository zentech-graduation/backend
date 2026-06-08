package com.app.modules.hashtag.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.app.modules.hashtag.entity.Hashtag;

@Repository
public interface HashtagRepository extends JpaRepository<Hashtag, UUID> {

    Optional<Hashtag> findByNameIgnoreCase(String name);

    /**
     * Inserts a hashtag if absent; relies on the unique constraint on name. No-op when the name
     * already exists.
     *
     * @param name the normalized hashtag name to insert
     */
    @Modifying
    @Query(
            value = "INSERT INTO hashtags (name) VALUES (:name) ON CONFLICT (name) DO NOTHING",
            nativeQuery = true)
    void upsertByName(@Param("name") String name);

    /**
     * Fuzzy hashtag search using the pg_trgm similarity operator, ordered by popularity then name.
     *
     * @param query the search term matched against {@code name} via the {@code %} trigram operator
     * @param limit maximum number of rows to return
     * @param offset number of leading rows to skip for pagination
     * @return matching hashtags ordered by {@code post_count} descending, then {@code name}
     *     ascending
     */
    @Query(
            value =
                    "SELECT * FROM hashtags WHERE name % :query ORDER BY post_count DESC, name ASC"
                            + " LIMIT :limit OFFSET :offset",
            nativeQuery = true)
    List<Hashtag> searchByNameTrgm(
            @Param("query") String query, @Param("limit") int limit, @Param("offset") int offset);

    /**
     * Returns scalar index projections for the given hashtag ids.
     *
     * <p>Selects columns directly rather than loading managed entities so the trigger-updated
     * {@code post_count} is read from the current database state within the transaction and is not
     * served from the Hibernate L1 cache.
     *
     * @param ids the hashtag ids to project
     * @return index projections for the matching hashtags
     */
    @Query(
            "SELECT h.id AS id, h.name AS name, h.postCount AS postCount, h.createdAt AS createdAt"
                    + " FROM Hashtag h WHERE h.id IN :ids")
    List<HashtagIndexProjection> findIndexProjectionsByIdIn(@Param("ids") Collection<UUID> ids);
}
