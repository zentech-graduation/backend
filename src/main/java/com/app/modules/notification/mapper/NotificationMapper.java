package com.app.modules.notification.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import com.app.common.response.UserSummaryResponse;
import com.app.modules.notification.dto.response.NotificationResponse;
import com.app.modules.notification.entity.Notification;

@Mapper(componentModel = "spring")
public interface NotificationMapper {

    /**
     * Builds the notification response from the entity and its separately resolved actor summary.
     *
     * @param notification the source notification; scalar fields map by name
     * @param actor the triggering user's public summary, resolved in a batch by the caller; null
     *     when the notification has no actor
     * @return the notification response with the actor embedded
     */
    @Mapping(source = "notification.id", target = "id")
    @Mapping(source = "actor", target = "actor")
    // Explicit because a boolean field and record component both named isRead resolve to
    // different property names under MapStruct's bean-property convention (source "read" via the
    // Lombok-generated isRead() getter, target "isRead" via the record accessor of the same name),
    // so the two sides never auto-match.
    @Mapping(target = "isRead", expression = "java(notification.isRead())")
    NotificationResponse toResponse(Notification notification, UserSummaryResponse actor);
}
