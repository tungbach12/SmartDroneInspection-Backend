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

@Entity
@Table(name = "inspections")
public class Inspection {

  @Id @GeneratedValue private UUID id;

  @Column(name = "organization_id", nullable = false)
  private UUID organizationId;

  @Column(name = "partner_assignment", nullable = false)
  private boolean partnerAssignment;

  @Column(name = "asset_id", nullable = false)
  private UUID assetId;

  @Column(name = "inspector_id", nullable = false)
  private UUID inspectorId;

  @Column(name = "drone_id")
  private UUID droneId;

  @Column(name = "planned_start_at")
  private Instant plannedStartAt;

  @Column(name = "planned_end_at")
  private Instant plannedEndAt;

  @Column(name = "flight_permit_file_key", length = 1000)
  private String flightPermitFileKey;

  @Column(name = "flight_permit_file_name", length = 500)
  private String flightPermitFileName;

  @Column(name = "flight_permit_uploaded_at")
  private Instant flightPermitUploadedAt;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private InspectionStatus status;

  @Column(name = "findings_confirmed_at")
  private Instant findingsConfirmedAt;

  @Column(name = "findings_snapshot_hash", length = 64)
  private String findingsSnapshotHash;

  @Column(name = "recurring_schedule_id")
  private UUID recurringScheduleId;

  @Column(name = "partner_assignment", nullable = false)
  private boolean partnerAssignment;

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
      UUID inspectorId,
      UUID droneId,
      Instant plannedStartAt,
      Instant plannedEndAt) {
    this.organizationId =
        Objects.requireNonNull(organizationId, "Inspection organization is required");
    this.assetId = Objects.requireNonNull(assetId, "Inspection asset is required");
    this.inspectorId = inspectorId;
    this.droneId = droneId;
    if (plannedStartAt != null && plannedEndAt != null && !plannedEndAt.isAfter(plannedStartAt)) {
      throw new IllegalArgumentException("Planned end must be after planned start");
    }
    this.plannedStartAt = plannedStartAt;
    this.plannedEndAt = plannedEndAt;
    this.status =
        (inspectorId != null && droneId != null)
            ? InspectionStatus.ASSIGNED
            : InspectionStatus.DRAFT;
    this.createdAt = Instant.now();
    this.updatedAt = createdAt;
  }

  public Inspection(UUID organizationId, UUID assetId, Instant plannedStartAt) {
    this(organizationId, assetId, null, null, plannedStartAt, null);
  }

  /** Marks an inspection assigned by the partner registry or reinspection path. */
  public void markPartnerAssignment() {
    if (inspectorId == null || droneId == null || status != InspectionStatus.ASSIGNED) {
      throw new IllegalStateException("A partner inspection must be fully assigned");
    }
    partnerAssignment = true;
  }

  public boolean isPartnerAssignment() {
    return partnerAssignment;
  }

  public void recordFlightPermit(String fileKey, String fileName, Instant uploadedAt) {
    if (fileKey == null || fileKey.isBlank()) {
      throw new IllegalArgumentException("Flight permit file key must not be blank");
    }
    if (fileName == null || fileName.isBlank()) {
      throw new IllegalArgumentException("Flight permit file name must not be blank");
    }
    this.flightPermitFileKey = fileKey.trim();
    this.flightPermitFileName = fileName.trim();
    this.flightPermitUploadedAt = uploadedAt != null ? uploadedAt : Instant.now();
    if (this.status == InspectionStatus.ASSIGNED) {
      this.status = InspectionStatus.READY_FOR_FLIGHT;
    }
    this.updatedAt = Instant.now();
  }

  public boolean hasFlightPermit() {
    return flightPermitFileKey != null && !flightPermitFileKey.isBlank();
  }

  /** MF3-06: the Inspector confirms the reviewed evidence/findings baseline before drafting. */
  public void confirmFindings(String snapshotHash) {
    if (status != InspectionStatus.FIELD_COMPLETED && status != InspectionStatus.REPORT_DRAFT) {
      throw new IllegalStateException("Findings can only be confirmed after field completion");
    }
    if (snapshotHash == null || snapshotHash.isBlank()) {
      throw new IllegalArgumentException("Findings confirmation requires a snapshot");
    }
    findingsConfirmedAt = Instant.now();
    findingsSnapshotHash = snapshotHash;
    status = InspectionStatus.REPORT_DRAFT;
    updatedAt = findingsConfirmedAt;
  }

  /** A pre-submission edit invalidates the reviewed set and returns the inspection to Findings. */
  public void reopenFindings() {
    if (status != InspectionStatus.REPORT_DRAFT) return;
    findingsConfirmedAt = null;
    findingsSnapshotHash = null;
    status = InspectionStatus.FIELD_COMPLETED;
    updatedAt = Instant.now();
  }

  public boolean hasCurrentFindingsConfirmation(String snapshotHash) {
    return (status == InspectionStatus.REPORT_DRAFT
            || status == InspectionStatus.REPORT_PUBLISHED
            || status == InspectionStatus.REPAIR_PENDING
            || status == InspectionStatus.COMPLETED)
        && findingsConfirmedAt != null
        && findingsSnapshotHash != null
        && findingsSnapshotHash.equals(snapshotHash);
  }

  public Instant getFindingsConfirmedAt() {
    return findingsConfirmedAt;
  }

  public String getFindingsSnapshotHash() {
    return findingsSnapshotHash;
  }

  public boolean hasFindingsConfirmation() {
    return findingsConfirmedAt != null && findingsSnapshotHash != null;
  }

  /**
   * Legacy transition retained for migration compatibility with existing persisted report states.
   */
  public void beginReportDraft() {
    requireStatus(InspectionStatus.FIELD_COMPLETED);
    status = InspectionStatus.REPORT_DRAFT;
    updatedAt = Instant.now();
  }

  /** MF3-11 publication. */
  public void markReportPublished() {
    if (status != InspectionStatus.REPORT_DRAFT
        && status != InspectionStatus.REPORT_PUBLISHED
        && status != InspectionStatus.REPAIR_PENDING
        && status != InspectionStatus.COMPLETED) {
      throw new IllegalStateException(
          "Inspection must be in drafting, active report, or closed status but is " + status);
    }
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

  public UUID getInspectorId() {
    return inspectorId;
  }

  public UUID getDroneId() {
    return droneId;
  }

  public String getFlightPermitFileKey() {
    return flightPermitFileKey;
  }

  public String getFlightPermitFileName() {
    return flightPermitFileName;
  }

  public Instant getFlightPermitUploadedAt() {
    return flightPermitUploadedAt;
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

  public UUID getRecurringScheduleId() {
    return recurringScheduleId;
  }

  public void setRecurringScheduleId(UUID recurringScheduleId) {
    this.recurringScheduleId = recurringScheduleId;
  }

  public boolean isInspectedBy(UUID candidateInspectorId) {
    return inspectorId != null && inspectorId.equals(candidateInspectorId);
  }

  public void dispatch() {
    if (status != InspectionStatus.DRAFT) {
      throw new IllegalStateException("Only a draft inspection can be dispatched");
    }
    moveTo(InspectionStatus.ASSIGNED);
  }

  public void completeFieldSession() {
    requireStatus(InspectionStatus.IN_PROGRESS);
    moveTo(InspectionStatus.FIELD_COMPLETED);
  }

  public void updatePlannedWindow(Instant plannedStartAt, Instant plannedEndAt) {
    if (plannedStartAt == null || plannedEndAt == null || !plannedEndAt.isAfter(plannedStartAt)) {
      throw new IllegalArgumentException("Planned end must be after planned start");
    }
    this.plannedStartAt = plannedStartAt;
    this.plannedEndAt = plannedEndAt;
    this.updatedAt = Instant.now();
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
