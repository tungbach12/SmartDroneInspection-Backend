package com.smartdroneinspection.users.domain;

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
@Table(name = "security_audit_events")
public class SecurityAuditEvent {

  @Id @GeneratedValue private UUID id;

  @Column(name = "actor_user_id")
  private UUID actorUserId;

  @Column(name = "subject_user_id")
  private UUID subjectUserId;

  @Column(name = "event_type", nullable = false, length = 96)
  private String eventType;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private SecurityAuditOutcome outcome;

  @Column(name = "ip_address", length = 45)
  private String ipAddress;

  @Column(name = "user_agent", length = 1000)
  private String userAgent;

  @Column(name = "correlation_id", length = 128)
  private String correlationId;

  @Column(name = "occurred_at", nullable = false, updatable = false)
  private Instant occurredAt;

  protected SecurityAuditEvent() {}

  public SecurityAuditEvent(
      UUID actorUserId,
      UUID subjectUserId,
      String eventType,
      SecurityAuditOutcome outcome,
      String ipAddress,
      String userAgent,
      String correlationId) {
    this.actorUserId = actorUserId;
    this.subjectUserId = subjectUserId;
    this.eventType = eventType;
    this.outcome = outcome;
    this.ipAddress = ipAddress;
    this.userAgent = userAgent;
    this.correlationId = correlationId;
    this.occurredAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getActorUserId() {
    return actorUserId;
  }

  public UUID getSubjectUserId() {
    return subjectUserId;
  }

  public String getEventType() {
    return eventType;
  }

  public SecurityAuditOutcome getOutcome() {
    return outcome;
  }

  public Instant getOccurredAt() {
    return occurredAt;
  }
}
