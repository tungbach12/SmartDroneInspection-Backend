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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

  @Column(name = "asset_pair_assignment_id")
  private UUID assetPairAssignmentId;

  @Column(nullable = false, length = 1000)
  private String objective;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private String scope;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "component_scope", columnDefinition = "jsonb")
  private String componentScope;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "acceptance_criteria", columnDefinition = "jsonb")
  private String acceptanceCriteria;

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
      String objective,
      String scope,
      String componentScope,
      String acceptanceCriteria,
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
    if (objective == null || objective.isBlank() || objective.length() > 1000) {
      throw new IllegalArgumentException(
          "Inspection objective is required (maximum 1000 characters)");
    }
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
    this.objective = objective;
    this.scope = Inspection.normalizeJson(scope);
    this.componentScope = Inspection.normalizeJson(componentScope);
    this.acceptanceCriteria = Inspection.normalizeJson(acceptanceCriteria);
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

  public RecurringInspectionSchedule(
      UUID organizationId,
      UUID assetId,
      UUID createdByUserId,
      UUID inspectorId,
      UUID droneId,
      UUID assetPairAssignmentId,
      String objective,
      String scope,
      String componentScope,
      String acceptanceCriteria,
      LocalDateTime anchor,
      String timezone,
      String unit,
      int interval,
      long durationSeconds,
      int reminderDays,
      Instant endsAt) {
    this(
        organizationId,
        assetId,
        createdByUserId,
        inspectorId,
        droneId,
        objective,
        scope,
        componentScope,
        acceptanceCriteria,
        anchor,
        timezone,
        unit,
        interval,
        durationSeconds,
        reminderDays,
        endsAt);
    this.assetPairAssignmentId = assetPairAssignmentId;
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

  public UUID getAssetPairAssignmentId() {
    return assetPairAssignmentId;
  }

  public String getObjective() {
    return objective;
  }

  public String getScope() {
    return scope;
  }

  public String getComponentScope() {
    return componentScope;
  }

  public String getAcceptanceCriteria() {
    return acceptanceCriteria;
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
