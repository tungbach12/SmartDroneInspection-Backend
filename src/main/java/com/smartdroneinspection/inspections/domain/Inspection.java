package com.smartdroneinspection.inspections.domain;

import com.smartdroneinspection.inspections.domain.enums.InspectionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inspections")
public class Inspection {

  @Id @GeneratedValue private UUID id;

  @Column(name = "service_order_id", nullable = false)
  private UUID serviceOrderId;

  @Column(name = "accepted_assignment_id", nullable = false)
  private UUID acceptedAssignmentId;

  @Column(name = "asset_id", nullable = false)
  private UUID assetId;

  @Column(name = "author_user_id", nullable = false)
  private UUID authorUserId;

  @Column(name = "checklist_template_id", nullable = false)
  private UUID checklistTemplateId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private InspectionStatus status;

  @Column(name = "started_at")
  private Instant startedAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "row_version", nullable = false)
  private long rowVersion;

  protected Inspection() {}

  public Inspection(
      UUID serviceOrderId,
      UUID acceptedAssignmentId,
      UUID assetId,
      UUID authorUserId,
      UUID checklistTemplateId) {
    this.serviceOrderId = serviceOrderId;
    this.acceptedAssignmentId = acceptedAssignmentId;
    this.assetId = assetId;
    this.authorUserId = authorUserId;
    this.checklistTemplateId = checklistTemplateId;
    this.status = InspectionStatus.READY_FOR_INSPECTION;
    this.createdAt = Instant.now();
    this.updatedAt = createdAt;
  }

  public UUID getId() {
    return id;
  }

  public UUID getServiceOrderId() {
    return serviceOrderId;
  }

  public UUID getAcceptedAssignmentId() {
    return acceptedAssignmentId;
  }

  public UUID getAssetId() {
    return assetId;
  }

  public UUID getAuthorUserId() {
    return authorUserId;
  }

  public UUID getChecklistTemplateId() {
    return checklistTemplateId;
  }

  public InspectionStatus getStatus() {
    return status;
  }

  public void start() {
    if (status == InspectionStatus.IN_PROGRESS) {
      return;
    }
    if (status != InspectionStatus.READY_FOR_INSPECTION) {
      throw new IllegalStateException("Only ready inspections can be started");
    }
    status = InspectionStatus.IN_PROGRESS;
    startedAt = Instant.now();
    updatedAt = startedAt;
  }

  public void complete() {
    if (status == InspectionStatus.COMPLETED) {
      return;
    }
    if (status != InspectionStatus.IN_PROGRESS && status != InspectionStatus.AWAITING_REPORT) {
      throw new IllegalStateException(
          "Only in-progress or awaiting-report inspections can be completed");
    }
    status = InspectionStatus.COMPLETED;
    completedAt = Instant.now();
    updatedAt = completedAt;
  }

  public Instant getStartedAt() {
    return startedAt;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }
}
