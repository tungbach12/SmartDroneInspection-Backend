package com.smartdroneinspection.assets.domain;

import com.smartdroneinspection.assets.domain.enums.AssetStatus;
import com.smartdroneinspection.assets.domain.enums.ChecklistTemplateStatus;
import com.smartdroneinspection.assets.domain.enums.InspectionFrequencyUnit;
import com.smartdroneinspection.assets.domain.enums.InspectionScheduleStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "inspection_schedules")
public class InspectionSchedule {

  @Id @GeneratedValue private UUID id;

  @Column(name = "asset_id", nullable = false)
  private UUID assetId;

  @Column(name = "checklist_template_id", nullable = false)
  private UUID checklistTemplateId;

  @Enumerated(EnumType.STRING)
  @Column(name = "frequency_unit", nullable = false, length = 16)
  private InspectionFrequencyUnit frequencyUnit;

  @Column(name = "frequency_interval", nullable = false)
  private int frequencyInterval;

  @Column(name = "next_due_at", nullable = false)
  private Instant nextDueAt;

  @Column(name = "last_generated_due_cycle")
  private LocalDate lastGeneratedDueCycle;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private InspectionScheduleStatus status;

  @Column(name = "created_by_user_id", nullable = false)
  private UUID createdByUserId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "row_version", nullable = false)
  private long rowVersion;

  protected InspectionSchedule() {}

  public InspectionSchedule(
      UUID assetId,
      UUID checklistTemplateId,
      InspectionFrequencyUnit frequencyUnit,
      int frequencyInterval,
      Instant nextDueAt,
      UUID createdByUserId) {
    if (frequencyInterval <= 0) {
      throw new IllegalArgumentException("Inspection schedule frequency interval must be positive");
    }
    this.assetId = assetId;
    this.checklistTemplateId = checklistTemplateId;
    this.frequencyUnit = frequencyUnit;
    this.frequencyInterval = frequencyInterval;
    this.nextDueAt = nextDueAt;
    this.status = InspectionScheduleStatus.PAUSED;
    this.createdByUserId = createdByUserId;
    this.createdAt = Instant.now();
    this.updatedAt = createdAt;
  }

  public void activate(AssetStatus assetStatus, ChecklistTemplateStatus checklistTemplateStatus) {
    if (assetStatus != AssetStatus.ACTIVE
        || checklistTemplateStatus != ChecklistTemplateStatus.ACTIVE) {
      throw new IllegalStateException(
          "Active schedule requires an active asset and checklist template");
    }
    status = InspectionScheduleStatus.ACTIVE;
    updatedAt = Instant.now();
  }

  public void pause() {
    status = InspectionScheduleStatus.PAUSED;
    updatedAt = Instant.now();
  }

  public void disable() {
    status = InspectionScheduleStatus.DISABLED;
    updatedAt = Instant.now();
  }

  public void markGenerated(LocalDate dueCycle, Instant nextDueAt) {
    if (status != InspectionScheduleStatus.ACTIVE) {
      throw new IllegalStateException("Only active schedules can generate inspection requests");
    }
    if (lastGeneratedDueCycle != null && dueCycle.isBefore(lastGeneratedDueCycle)) {
      throw new IllegalArgumentException("Due cycle cannot be older than the last generated cycle");
    }
    if (!nextDueAt.isAfter(this.nextDueAt)) {
      throw new IllegalArgumentException("Next due time must be after the current due time");
    }
    lastGeneratedDueCycle = dueCycle;
    this.nextDueAt = nextDueAt;
    updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getAssetId() {
    return assetId;
  }

  public UUID getChecklistTemplateId() {
    return checklistTemplateId;
  }

  public InspectionScheduleStatus getStatus() {
    return status;
  }

  public Instant getNextDueAt() {
    return nextDueAt;
  }

  public LocalDate getLastGeneratedDueCycle() {
    return lastGeneratedDueCycle;
  }
}
