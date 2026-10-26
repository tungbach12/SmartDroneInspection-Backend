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

  @Column(name = "deduplication_key", length = 192, unique = true)
  private String deduplicationKey;

  protected Notification() {}

  public Notification(
      UUID recipientUserId,
      UUID organizationId,
      String eventType,
      NotificationChannel channel,
      String title,
      String body,
      String targetPath) {
    this(recipientUserId, organizationId, eventType, channel, title, body, targetPath, null);
  }

  public Notification(
      UUID recipientUserId,
      UUID organizationId,
      String eventType,
      NotificationChannel channel,
      String title,
      String body,
      String targetPath,
      String deduplicationKey) {
    this.recipientUserId = recipientUserId;
    this.organizationId = organizationId;
    this.eventType = eventType;
    this.channel = channel;
    this.title = title;
    this.body = body;
    this.targetPath = targetPath;
    this.deduplicationKey = deduplicationKey;
    this.attemptCount = 0;
    this.createdAt = Instant.now();
    if (channel == NotificationChannel.IN_APP) {
      this.status = NotificationStatus.SENT;
      this.sentAt = this.createdAt;
    } else {
      this.status = NotificationStatus.PENDING;
    }
  }

  public void markRead() {
    if (this.readAt != null) {
      return;
    }
    Instant now = Instant.now();
    this.readAt = now;
    if (this.sentAt == null) {
      this.sentAt = now;
    }
    this.status = NotificationStatus.READ;
  }

  public void markAsRead() {
    markRead();
  }

  public boolean isRead() {
    return this.readAt != null || this.status == NotificationStatus.READ;
  }

  public UUID getId() {
    return id;
  }

  public UUID getRecipientUserId() {
    return recipientUserId;
  }

  public UUID getOrganizationId() {
    return organizationId;
  }

  public String getEventType() {
    return eventType;
  }

  public NotificationChannel getChannel() {
    return channel;
  }

  public String getTitle() {
    return title;
  }

  public String getBody() {
    return body;
  }

  public String getTargetPath() {
    return targetPath;
  }

  public NotificationStatus getStatus() {
    return status;
  }

  public int getAttemptCount() {
    return attemptCount;
  }

  public String getLastErrorCode() {
    return lastErrorCode;
  }

  public Instant getSentAt() {
    return sentAt;
  }

  public Instant getReadAt() {
    return readAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public String getDeduplicationKey() {
    return deduplicationKey;
  }
}
