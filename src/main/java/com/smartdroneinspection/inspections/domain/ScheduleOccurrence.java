package com.smartdroneinspection.inspections.domain;

import com.smartdroneinspection.inspections.domain.enums.OccurrenceStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "schedule_occurrences")
public class ScheduleOccurrence {

  @Id @GeneratedValue private UUID id;

  @Column(name = "schedule_id", nullable = false)
  private UUID scheduleId;

  @Column(name = "organization_id", nullable = false)
  private UUID organizationId;

  @Column(name = "occurrence_index", nullable = false)
  private long occurrenceIndex;

  @Column(name = "occurrence_key", nullable = false, length = 64)
  private String occurrenceKey;

  @Column(name = "planned_start_at", nullable = false)
  private Instant plannedStartAt;

  @Column(name = "planned_end_at", nullable = false)
  private Instant plannedEndAt;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private OccurrenceStatus status;

  @Column(name = "inspection_id")
  private UUID inspectionId;

  @Column(name = "blocked_reason", length = 1000)
  private String blockedReason;

  @Column(name = "retry_count", nullable = false)
  private int retryCount = 0;

  @Column(name = "reminder_sent_at")
  private Instant reminderSentAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "row_version", nullable = false)
  private long rowVersion;

  protected ScheduleOccurrence() {}

  public ScheduleOccurrence(
      UUID scheduleId,
      UUID organizationId,
      long occurrenceIndex,
      String occurrenceKey,
      Instant plannedStartAt,
      Instant plannedEndAt,
      OccurrenceStatus status) {
    this.scheduleId = Objects.requireNonNull(scheduleId, "Schedule ID is required");
    this.organizationId = Objects.requireNonNull(organizationId, "Organization ID is required");
    this.occurrenceIndex = occurrenceIndex;
    this.occurrenceKey = Objects.requireNonNull(occurrenceKey, "Occurrence key is required");
    this.plannedStartAt = Objects.requireNonNull(plannedStartAt, "Planned start is required");
    this.plannedEndAt = Objects.requireNonNull(plannedEndAt, "Planned end is required");
    this.status = Objects.requireNonNull(status, "Occurrence status is required");
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  @PrePersist
  void onCreate() {
    if (createdAt == null) {
      createdAt = Instant.now();
    }
    if (updatedAt == null) {
      updatedAt = Instant.now();
    }
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }

  public void markGenerated(UUID inspectionId) {
    this.status = OccurrenceStatus.GENERATED;
    this.inspectionId = Objects.requireNonNull(inspectionId, "Inspection ID is required");
    this.blockedReason = null;
  }

  public void markBlocked(String reason) {
    this.status = OccurrenceStatus.BLOCKED;
    this.blockedReason = reason;
  }

  public void markSkipped(String reason) {
    if (this.status != OccurrenceStatus.BLOCKED) {
      throw new IllegalStateException("Only blocked occurrences can be skipped");
    }
    this.status = OccurrenceStatus.SKIPPED;
    this.blockedReason = reason;
  }

  public void recordRetry() {
    this.retryCount++;
  }

  public void markReminderSent(Instant sentAt) {
    this.reminderSentAt = sentAt;
  }

  // Getters
  public UUID getId() {
    return id;
  }

  public UUID getScheduleId() {
    return scheduleId;
  }

  public UUID getOrganizationId() {
    return organizationId;
  }

  public long getOccurrenceIndex() {
    return occurrenceIndex;
  }

  public String getOccurrenceKey() {
    return occurrenceKey;
  }

  public Instant getPlannedStartAt() {
    return plannedStartAt;
  }

  public Instant getPlannedEndAt() {
    return plannedEndAt;
  }

  public OccurrenceStatus getStatus() {
    return status;
  }

  public UUID getInspectionId() {
    return inspectionId;
  }

  public String getBlockedReason() {
    return blockedReason;
  }

  public int getRetryCount() {
    return retryCount;
  }

  public Instant getReminderSentAt() {
    return reminderSentAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public long getRowVersion() {
    return rowVersion;
  }
}
