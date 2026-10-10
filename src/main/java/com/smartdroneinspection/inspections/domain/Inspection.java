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
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "inspections")
public class Inspection {

  @Id @GeneratedValue private UUID id;

  @Column(name = "organization_id", nullable = false)
  private UUID organizationId;

  @Column(name = "asset_id", nullable = false)
  private UUID assetId;

  @Column(name = "schedule_id")
  private UUID scheduleId;

  @Column(name = "asset_pair_assignment_id")
  private UUID assetPairAssignmentId;

  @Column(name = "inspector_id", nullable = false)
  private UUID inspectorId;

  @Column(name = "drone_id")
  private UUID droneId;

  @Column(name = "due_cycle_key", length = 64)
  private String dueCycleKey;

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

  @Column(name = "planned_start_at")
  private Instant plannedStartAt;

  @Column(name = "planned_end_at")
  private Instant plannedEndAt;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private InspectionStatus status;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "row_version", nullable = false)
  private long rowVersion;

  protected Inspection() {}

  public Inspection(
      UUID organizationId,
      UUID assetId,
      UUID scheduleId,
      UUID assetPairAssignmentId,
      UUID inspectorId,
      UUID droneId,
      String dueCycleKey,
      String objective,
      String scope,
      String componentScope,
      String acceptanceCriteria,
      Instant plannedStartAt,
      Instant plannedEndAt) {
    this.organizationId = organizationId;
    this.assetId = assetId;
    this.scheduleId = scheduleId;
    this.assetPairAssignmentId = assetPairAssignmentId;
    this.inspectorId = inspectorId;
    this.droneId = droneId;
    this.dueCycleKey = dueCycleKey;
    this.objective = objective;
    this.scope = scope;
    this.componentScope = componentScope;
    this.acceptanceCriteria = acceptanceCriteria;
    this.plannedStartAt = plannedStartAt;
    this.plannedEndAt = plannedEndAt;
    this.status = InspectionStatus.DRAFT;
    this.createdAt = Instant.now();
    this.updatedAt = createdAt;
  }

  public Inspection(UUID organizationId, UUID assetId, String objective, Instant plannedStartAt) {
    this(
        Objects.requireNonNull(organizationId, "Inspection organization is required"),
        Objects.requireNonNull(assetId, "Inspection asset is required"),
        null,
        null,
        null,
        null,
        null,
        objective,
        null,
        null,
        null,
        plannedStartAt,
        null);
  }

  /**
   * MF3-04 snapshots the confirmed evidence set, which moves the inspection into report drafting.
   */
  public void beginReportDraft() {
    requireStatus(InspectionStatus.FIELD_COMPLETED);
    status = InspectionStatus.REPORT_DRAFT;
    updatedAt = Instant.now();
  }

  /** MF3-11 publication. */
  public void markReportPublished() {
    requireStatus(InspectionStatus.REPORT_DRAFT);
    status = InspectionStatus.REPORT_PUBLISHED;
    updatedAt = Instant.now();
  }

  /** MF3-12: confirmed repair-required findings were handed to MF4. */
  public void markRepairPending() {
    requireStatus(InspectionStatus.REPORT_PUBLISHED);
    status = InspectionStatus.REPAIR_PENDING;
    updatedAt = Instant.now();
  }

  /** MF3-13: no corrective work was required within the observed scope. */
  public void complete() {
    if (status != InspectionStatus.REPORT_PUBLISHED && status != InspectionStatus.REPAIR_PENDING) {
      throw new IllegalStateException("Only a published inspection can be completed");
    }
    status = InspectionStatus.COMPLETED;
    updatedAt = Instant.now();
  }

  private void requireStatus(InspectionStatus expected) {
    if (status != expected) {
      throw new IllegalStateException("Inspection must be " + expected + " but is " + status);
    }
  }

  public UUID getId() {
    return id;
  }

  public UUID getOrganizationId() {
    return organizationId;
  }

  public UUID getAssetId() {
    return assetId;
  }

  public UUID getScheduleId() {
    return scheduleId;
  }

  public UUID getAssetPairAssignmentId() {
    return assetPairAssignmentId;
  }

  public UUID getInspectorId() {
    return inspectorId;
  }

  public UUID getDroneId() {
    return droneId;
  }

  public String getDueCycleKey() {
    return dueCycleKey;
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

  public Instant getPlannedStartAt() {
    return plannedStartAt;
  }

  public Instant getPlannedEndAt() {
    return plannedEndAt;
  }

  public InspectionStatus getStatus() {
    return status;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public boolean isInspectedBy(UUID candidateInspectorId) {
    return inspectorId != null && inspectorId.equals(candidateInspectorId);
  }

  public void startPreparation() {
    if (status != InspectionStatus.ASSIGNED) {
      throw new IllegalStateException("Only an assigned inspection can start preparation");
    }
    moveTo(InspectionStatus.PREPARING);
  }

  public void moveTo(InspectionStatus nextStatus) {
    Objects.requireNonNull(nextStatus, "Inspection status is required");
    if (nextStatus == this.status) {
      throw new IllegalStateException("The inspection is already " + nextStatus);
    }
    this.status = nextStatus;
    this.updatedAt = Instant.now();
  }
}
