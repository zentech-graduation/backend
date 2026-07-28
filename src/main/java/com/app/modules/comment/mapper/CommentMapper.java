package com.app.modules.comment.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.app.common.response.UserSummaryResponse;
import com.app.modules.comment.dto.response.CommentResponse;
import com.app.modules.comment.entity.Comment;

/** Maps comment entities to API response DTOs. */
@Mapper(componentModel = "spring")
public interface CommentMapper {

    /**
     * Builds the comment response from the entity and its separately resolved author summary.
     *
     * @param comment the source comment; scalar fields map by name
     * @param author the comment author's public summary, resolved in a batch by the service
     * @return the comment response with the author embedded
     */
    @Mapping(source = "comment.id", target = "id")
    @Mapping(source = "author", target = "author")
    CommentResponse toResponse(Comment comment, UserSummaryResponse author);
}
