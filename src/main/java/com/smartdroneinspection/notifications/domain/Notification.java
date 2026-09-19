package com.smartdroneinspection.notifications.domain;

import com.smartdroneinspection.notifications.domain.enums.NotificationChannel;
import com.smartdroneinspection.notifications.domain.enums.NotificationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notifications")
public class Notification {

  @Id @GeneratedValue private UUID id;

  @Column(name = "recipient_user_id", nullable = false)
  private UUID recipientUserId;

  @Column(name = "organization_id")
  private UUID organizationId;

  @Column(name = "event_type", nullable = false, length = 96)
  private String eventType;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private NotificationChannel channel;

  @Column(nullable = false, length = 300)
  private String title;

  @Column(nullable = false, length = 4000)
  private String body;

  @Column(name = "target_path", length = 1000)
  private String targetPath;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private NotificationStatus status;

  @Column(name = "attempt_count", nullable = false)
  private int attemptCount;

  @Column(name = "last_error_code", length = 96)
  private String lastErrorCode;

  @Column(name = "sent_at")
  private Instant sentAt;

  @Column(name = "read_at")
  private Instant readAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected Notification() {}

  public Notification(
      UUID recipientUserId,
      UUID organizationId,
      String eventType,
      NotificationChannel channel,
      String title,
      String body,
      String targetPath) {
    this.recipientUserId = recipientUserId;
    this.organizationId = organizationId;
    this.eventType = eventType;
    this.channel = channel;
    this.title = title;
    this.body = body;
    this.targetPath = targetPath;
    this.status = NotificationStatus.PENDING;
    this.attemptCount = 0;
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getRecipientUserId() {
    return recipientUserId;
  }

  public NotificationStatus getStatus() {
    return status;
  }
}
