package com.app.modules.notification.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.app.modules.notification.dto.response.NotificationResponse;
import com.app.modules.notification.entity.Notification;

@Mapper(componentModel = "spring")
public interface NotificationMapper {

    /**
     * Converts a {@link Notification} entity to its API response representation. The {@code type}
     * field is mapped to the lowercase string form of the enum constant.
     */
    @Mapping(target = "type", expression = "java(notification.getType().name().toLowerCase())")
    NotificationResponse toResponse(Notification notification);

    List<NotificationResponse> toResponseList(List<Notification> notifications);
}
