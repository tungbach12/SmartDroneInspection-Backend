package com.smartdroneinspection.notifications.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartdroneinspection.notifications.domain.enums.NotificationChannel;
import com.smartdroneinspection.notifications.domain.enums.NotificationStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationTest {

  @Test
  void inAppNotificationInitializesSent() {
    UUID recipientId = UUID.randomUUID();
    UUID orgId = UUID.randomUUID();
    String deduplicationKey = "INSPECTION_ASSIGNED:123:" + recipientId;

    Notification notification =
        new Notification(
            recipientId,
            orgId,
            "INSPECTION_ASSIGNED",
            NotificationChannel.IN_APP,
            "Assigned",
            "Details",
            "/inspections/123",
            deduplicationKey);

    assertThat(notification.getRecipientUserId()).isEqualTo(recipientId);
    assertThat(notification.getOrganizationId()).isEqualTo(orgId);
    assertThat(notification.getEventType()).isEqualTo("INSPECTION_ASSIGNED");
    assertThat(notification.getChannel()).isEqualTo(NotificationChannel.IN_APP);
    assertThat(notification.getTitle()).isEqualTo("Assigned");
    assertThat(notification.getBody()).isEqualTo("Details");
    assertThat(notification.getTargetPath()).isEqualTo("/inspections/123");
    assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SENT);
    assertThat(notification.getCreatedAt()).isNotNull();
    assertThat(notification.getSentAt()).isEqualTo(notification.getCreatedAt());
    assertThat(notification.getReadAt()).isNull();
    assertThat(notification.isRead()).isFalse();
    assertThat(notification.getDeduplicationKey()).isEqualTo(deduplicationKey);
    assertThat(notification.getAttemptCount()).isZero();
    assertThat(notification.getLastErrorCode()).isNull();
  }

  @Test
  void emailNotificationInitializesPending() {
    UUID recipientId = UUID.randomUUID();
    UUID orgId = UUID.randomUUID();

    Notification notification =
        new Notification(
            recipientId,
            orgId,
            "WORK_ORDER_ASSIGNED",
            NotificationChannel.EMAIL,
            "Title",
            "Body",
            "/target");

    assertThat(notification.getStatus()).isEqualTo(NotificationStatus.PENDING);
    assertThat(notification.getSentAt()).isNull();
    assertThat(notification.getReadAt()).isNull();
    assertThat(notification.isRead()).isFalse();
    assertThat(notification.getDeduplicationKey()).isNull();
  }

  @Test
  void markReadIsIdempotent() {
    UUID recipientId = UUID.randomUUID();
    UUID orgId = UUID.randomUUID();

    Notification notification =
        new Notification(
            recipientId,
            orgId,
            "INSPECTION_ASSIGNED",
            NotificationChannel.IN_APP,
            "Assigned",
            "Details",
            "/inspections/123");

    notification.markRead();

    assertThat(notification.getStatus()).isEqualTo(NotificationStatus.READ);
    assertThat(notification.isRead()).isTrue();
    Instant firstReadAt = notification.getReadAt();
    assertThat(firstReadAt).isNotNull();

    // Call markRead again, readAt must remain unchanged
    notification.markRead();
    assertThat(notification.getReadAt()).isEqualTo(firstReadAt);

    // markAsRead alias must also be idempotent
    notification.markAsRead();
    assertThat(notification.getReadAt()).isEqualTo(firstReadAt);
  }

  @Test
  void markReadOnPendingNotificationSetsSentAt() {
    UUID recipientId = UUID.randomUUID();
    UUID orgId = UUID.randomUUID();

    Notification notification =
        new Notification(
            recipientId,
            orgId,
            "INSPECTION_ASSIGNED",
            NotificationChannel.EMAIL,
            "Title",
            "Body",
            "/path");

    assertThat(notification.getSentAt()).isNull();

    notification.markRead();

    assertThat(notification.getStatus()).isEqualTo(NotificationStatus.READ);
    assertThat(notification.getReadAt()).isNotNull();
    assertThat(notification.getSentAt()).isEqualTo(notification.getReadAt());
    assertThat(notification.isRead()).isTrue();
  }
}
