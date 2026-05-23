package com.app.common.security.user;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.app.modules.users.entity.User;

/** Maps {@link User} entities to security-layer principals. */
@Mapper(componentModel = "spring")
public interface SecurityMapper {

    @Mapping(source = "id", target = "userId")
    @Mapping(source = "role", target = "role")
    @Mapping(source = "status", target = "status")
    UserPrincipal toUserPrincipal(User user);
}
