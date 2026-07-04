package com.app.modules.comment.mapper;

import org.mapstruct.Mapper;

import com.app.modules.comment.dto.response.CommentResponse;
import com.app.modules.comment.entity.Comment;

/** Maps comment entities to API response DTOs. */
@Mapper(componentModel = "spring")
public interface CommentMapper {

    CommentResponse toResponse(Comment comment);
}
