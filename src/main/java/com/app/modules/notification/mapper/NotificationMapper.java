package com.app.modules.notification.mapper;

import java.util.List;

import org.mapstruct.Mapper;

import com.app.modules.notification.dto.response.NotificationResponse;
import com.app.modules.notification.entity.Notification;

@Mapper(componentModel = "spring")
public interface NotificationMapper {

    /** Converts a {@link Notification} entity to its API response representation. */
    NotificationResponse toResponse(Notification notification);

    List<NotificationResponse> toResponseList(List<Notification> notifications);
}
