package com.app.modules.hashtag.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.app.modules.hashtag.dto.response.HashtagResponse;
import com.app.modules.hashtag.dto.response.HashtagTrendingResponse;
import com.app.modules.hashtag.entity.Hashtag;
import com.app.modules.hashtag.entity.HashtagTrending;
import com.app.modules.hashtag.search.HashtagDocument;

/** Maps hashtag entities and search documents to and from API DTOs. */
@Mapper(componentModel = "spring")
public interface HashtagMapper {

    HashtagResponse toResponse(Hashtag hashtag);

    /**
     * Flattens the embedded composite key onto the response and injects the separately resolved
     * hashtag name.
     *
     * @param trending the trending snapshot row carrying the composite key
     * @param hashtagName the resolved hashtag name, not stored on the trending row
     * @return the trending response with key fields and name populated
     */
    @Mapping(source = "trending.id.hashtagId", target = "hashtagId")
    @Mapping(source = "trending.id.periodStart", target = "periodStart")
    @Mapping(source = "hashtagName", target = "name")
    HashtagTrendingResponse toTrendingResponse(HashtagTrending trending, String hashtagName);

    /**
     * Projects the entity to its Elasticsearch document, converting the UUID id to its string form.
     *
     * @param hashtag the source entity
     * @return the indexable document
     */
    @Mapping(target = "id", expression = "java(hashtag.getId().toString())")
    @Mapping(source = "name", target = "name")
    @Mapping(source = "postCount", target = "postCount")
    @Mapping(source = "createdAt", target = "createdAt")
    HashtagDocument toDocument(Hashtag hashtag);

    /**
     * Rebuilds the response from a search document, parsing the string id back into a UUID.
     *
     * @param document the Elasticsearch document
     * @return the response with the id parsed back to a UUID
     */
    @Mapping(target = "id", expression = "java(java.util.UUID.fromString(document.getId()))")
    HashtagResponse fromDocument(HashtagDocument document);
}
