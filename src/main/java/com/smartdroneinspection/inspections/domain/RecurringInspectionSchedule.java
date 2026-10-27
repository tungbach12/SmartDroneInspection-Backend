package com.smartdroneinspection.inspections.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/** Recurrence definition; generated inspections keep their own immutable assignment snapshots. */
@Entity
@Table(name = "recurring_inspection_schedules")
public class RecurringInspectionSchedule {

  @Id @GeneratedValue private UUID id;

  @Column(name = "organization_id", nullable = false)
  private UUID organizationId;

  @Column(name = "asset_id", nullable = false)
  private UUID assetId;

  @Column(name = "created_by_user_id", nullable = false)
  private UUID createdByUserId;

  @Column(name = "inspector_id", nullable = false)
  private UUID inspectorId;

  @Column(name = "drone_id", nullable = false)
  private UUID droneId;

  @Column(name = "anchor_date", nullable = false)
  private LocalDateTime anchorDate;

  @Column(nullable = false, length = 64)
  private String timezone;

  @Column(name = "recurrence_unit", nullable = false, length = 16)
  private String recurrenceUnit;

  @Column(name = "recurrence_interval", nullable = false)
  private int recurrenceInterval;

  @Column(name = "duration_seconds", nullable = false)
  private long durationSeconds;

  @Column(name = "reminder_days", nullable = false)
  private int reminderDays;

  @Column(name = "ends_at")
  private Instant endsAt;

  @Column(name = "next_occurrence", nullable = false)
  private long nextOccurrence;

  @Column(nullable = false, length = 32)
  private String status = "ACTIVE";

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "row_version", nullable = false)
  private long rowVersion;

  @Transient private InspectionRecurrence recurrence;

  protected RecurringInspectionSchedule() {}

  public RecurringInspectionSchedule(
      UUID organizationId,
      UUID assetId,
      UUID createdByUserId,
      UUID inspectorId,
      UUID droneId,
      LocalDateTime anchor,
      String timezone,
      String unit,
      int interval,
      long durationSeconds,
      int reminderDays,
      Instant endsAt) {
    this.organizationId = Objects.requireNonNull(organizationId, "Organization ID is required");
    this.assetId = Objects.requireNonNull(assetId, "Asset ID is required");
    this.createdByUserId =
        Objects.requireNonNull(createdByUserId, "Created by user ID is required");
    this.inspectorId = Objects.requireNonNull(inspectorId, "Inspector ID is required");
    this.droneId = Objects.requireNonNull(droneId, "Drone ID is required");
    if (durationSeconds < 1 || durationSeconds > 604800) {
      throw new IllegalArgumentException(
          "Inspection duration must be between one second and seven days");
    }
    if (reminderDays < 0 || reminderDays > 30) {
      throw new IllegalArgumentException(
          "Reminder must be between 0 and 30 days before inspection");
    }
    this.recurrence = new InspectionRecurrence(anchor, timezone, unit, interval);
    if (endsAt != null && endsAt.isBefore(this.recurrence.occurrenceAt(0))) {
      throw new IllegalArgumentException("Schedule end must not precede the first occurrence");
    }
    this.anchorDate = anchor;
    this.timezone = timezone;
    this.recurrenceUnit = unit;
    this.recurrenceInterval = interval;
    this.durationSeconds = durationSeconds;
    this.reminderDays = reminderDays;
    this.endsAt = endsAt;
    this.nextOccurrence = 0;
    this.status = "ACTIVE";
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

  public InspectionRecurrence getRecurrence() {
    if (recurrence == null) {
      recurrence =
          new InspectionRecurrence(anchorDate, timezone, recurrenceUnit, recurrenceInterval);
    }
    return recurrence;
  }

  public Instant nextDueAt() {
    return getRecurrence().occurrenceAt(nextOccurrence);
  }

  public boolean isActive() {
    return "ACTIVE".equals(status);
  }

  public boolean isPaused() {
    return "PAUSED".equals(status);
  }

  public boolean isEnded() {
    return "ENDED".equals(status);
  }

  public void pause() {
    requireNotEnded();
    status = "PAUSED";
  }

  public void resume() {
    requireNotEnded();
    status = "ACTIVE";
  }

  public void end() {
    status = "ENDED";
  }

  public void advance() {
    if (!isActive()) {
      throw new IllegalStateException("Only an active schedule can advance");
    }
    nextOccurrence = Math.incrementExact(nextOccurrence);
    if (endsAt != null && nextDueAt().isAfter(endsAt)) {
      end();
    }
  }

  private void requireNotEnded() {
    if ("ENDED".equals(status)) {
      throw new IllegalStateException("An ended schedule cannot resume");
    }
  }

  // Getters
  public UUID getId() {
    return id;
  }

  public UUID getOrganizationId() {
    return organizationId;
  }

  public UUID getAssetId() {
    return assetId;
  }

  public UUID getCreatedByUserId() {
    return createdByUserId;
  }

  public UUID getInspectorId() {
    return inspectorId;
  }

  public UUID getDroneId() {
    return droneId;
  }

  public LocalDateTime getAnchorDate() {
    return anchorDate;
  }

  public String getTimezone() {
    return timezone;
  }

  public String getRecurrenceUnit() {
    return recurrenceUnit;
  }

  public int getRecurrenceInterval() {
    return recurrenceInterval;
  }

  public long getDurationSeconds() {
    return durationSeconds;
  }

  public int getReminderDays() {
    return reminderDays;
  }

  public Instant getEndsAt() {
    return endsAt;
  }

  public long getNextOccurrence() {
    return nextOccurrence;
  }

  public String getStatus() {
    return status;
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
