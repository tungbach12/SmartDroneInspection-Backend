package com.smartdroneinspection.inspections.domain;

import com.smartdroneinspection.inspections.domain.enums.DefectResolutionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * MF4 targeted defect verification during an MF3 re-inspection.
 *
 * <p>Tracks the verification status and fresh evidence for an original finding from a prior
 * inspection after maintenance was performed.
 */
@Entity
@Table(
    name = "reinspection_defect_verifications",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_reinspection_defect",
            columnNames = {"reinspection_id", "source_finding_id"}))
public class ReinspectionDefectVerification {

  @Id @GeneratedValue private UUID id;

  @Column(name = "reinspection_id", nullable = false)
  private UUID reinspectionId;

  @Column(name = "source_finding_id", nullable = false)
  private UUID sourceFindingId;

  @Column(name = "new_evidence_id")
  private UUID newEvidenceId;

  @Enumerated(EnumType.STRING)
  @Column(name = "resolution_status", nullable = false, length = 32)
  private DefectResolutionStatus resolutionStatus = DefectResolutionStatus.UNRESOLVED;

  @Column(name = "inspector_notes", length = 4000)
  private String inspectorNotes;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  protected ReinspectionDefectVerification() {}

  public ReinspectionDefectVerification(UUID reinspectionId, UUID sourceFindingId) {
    this.reinspectionId = Objects.requireNonNull(reinspectionId, "reinspectionId is required");
    this.sourceFindingId = Objects.requireNonNull(sourceFindingId, "sourceFindingId is required");
    this.resolutionStatus = DefectResolutionStatus.UNRESOLVED;
    this.createdAt = Instant.now();
    this.updatedAt = this.createdAt;
  }

  public void updateVerification(
      DefectResolutionStatus status, UUID newEvidenceId, String inspectorNotes) {
    this.resolutionStatus = Objects.requireNonNull(status, "resolutionStatus is required");
    this.newEvidenceId = newEvidenceId;
    this.inspectorNotes = inspectorNotes;
    this.updatedAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getReinspectionId() {
    return reinspectionId;
  }

  public UUID getSourceFindingId() {
    return sourceFindingId;
  }

  public UUID getNewEvidenceId() {
    return newEvidenceId;
  }

  public DefectResolutionStatus getResolutionStatus() {
    return resolutionStatus;
  }

  public String getInspectorNotes() {
    return inspectorNotes;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
