package com.nexatech.notification.mapper;

import com.nexatech.notification.dto.NotificationResponse;
import com.nexatech.notification.model.Notification;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface NotificationMapper {

    NotificationResponse toResponse(Notification notification);
}
