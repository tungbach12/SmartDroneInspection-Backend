package com.smartdroneinspection.notifications.api.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.smartdroneinspection.notifications.domain.Notification;
import com.smartdroneinspection.notifications.domain.enums.NotificationChannel;
import com.smartdroneinspection.notifications.domain.enums.NotificationStatus;
import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
    UUID id,
    String eventType,
    NotificationChannel channel,
    String title,
    String body,
    String targetPath,
    NotificationStatus status,
    Instant readAt,
    Instant createdAt,
    @JsonProperty("read") boolean read) {

  public static NotificationResponse from(Notification notification) {
    return new NotificationResponse(
        notification.getId(),
        notification.getEventType(),
        notification.getChannel(),
        notification.getTitle(),
        notification.getBody(),
        notification.getTargetPath(),
        notification.getStatus(),
        notification.getReadAt(),
        notification.getCreatedAt(),
        notification.isRead());
  }

  @JsonProperty("isRead")
  public boolean isRead() {
    return read;
  }
}
