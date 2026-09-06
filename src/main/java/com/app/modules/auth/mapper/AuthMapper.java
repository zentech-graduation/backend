package com.app.modules.auth.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.app.modules.auth.dto.response.AuthenticatedUserResponse;
import com.app.modules.users.entity.User;

/** Maps {@link User} entities to auth-layer DTOs. */
@Mapper(componentModel = "spring")
public interface AuthMapper {

    @Mapping(source = "user.id", target = "id")
    @Mapping(source = "user.username", target = "username")
    @Mapping(source = "user.email", target = "email")
    @Mapping(source = "user.displayName", target = "displayName")
    @Mapping(source = "user.role", target = "role")
    @Mapping(source = "emailVerified", target = "emailVerified")
    AuthenticatedUserResponse toAuthenticatedUserResponse(User user, boolean emailVerified);
}
