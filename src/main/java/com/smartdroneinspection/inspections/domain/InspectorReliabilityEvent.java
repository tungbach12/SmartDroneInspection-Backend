package com.smartdroneinspection.inspections.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Immutable reliability ledger entry recording inspector decline, no-show, or abort outcomes. */
@Entity
@Table(name = "inspector_reliability_events")
public class InspectorReliabilityEvent {

  public enum EventType {
    DECLINE,
    NO_SHOW,
    ABORT,
    FORCE_ABORT
  }

  @Id @GeneratedValue private UUID id;

  @Column(name = "inspector_id", nullable = false)
  private UUID inspectorId;

  @Column(name = "inspection_id", nullable = false)
  private UUID inspectionId;

  @Column(name = "field_session_id")
  private UUID fieldSessionId;

  @Enumerated(EnumType.STRING)
  @Column(name = "event_type", nullable = false, length = 24)
  private EventType eventType;

  @Column(name = "reason", length = 2000)
  private String reason;

  @Column(name = "occurred_at", nullable = false, updatable = false)
  private Instant occurredAt;

  protected InspectorReliabilityEvent() {}

  public InspectorReliabilityEvent(
      UUID inspectorId,
      UUID inspectionId,
      UUID fieldSessionId,
      EventType eventType,
      String reason,
      Instant occurredAt) {
    this.inspectorId = Objects.requireNonNull(inspectorId, "Inspector ID is required");
    this.inspectionId = Objects.requireNonNull(inspectionId, "Inspection ID is required");
    this.fieldSessionId = fieldSessionId;
    this.eventType = Objects.requireNonNull(eventType, "Event type is required");
    this.reason = reason == null || reason.isBlank() ? null : reason.trim();
    this.occurredAt = occurredAt != null ? occurredAt : Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getInspectorId() {
    return inspectorId;
  }

  public UUID getInspectionId() {
    return inspectionId;
  }

  public UUID getFieldSessionId() {
    return fieldSessionId;
  }

  public EventType getEventType() {
    return eventType;
  }

  public String getReason() {
    return reason;
  }

  public Instant getOccurredAt() {
    return occurredAt;
  }
}
