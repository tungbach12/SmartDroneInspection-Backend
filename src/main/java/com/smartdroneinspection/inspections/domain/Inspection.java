package com.smartdroneinspection.inspections.domain;

import com.smartdroneinspection.inspections.domain.enums.AssignmentStatus;
import com.smartdroneinspection.inspections.domain.enums.FlightPermitVerificationStatus;
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

  @Column(name = "owner_customer_id", nullable = false)
  private UUID ownerCustomerId;

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

  @Column(name = "flight_permit_number", length = 120)
  private String flightPermitNumber;

  @Column(name = "flight_permit_authority", length = 200)
  private String flightPermitAuthority;

  @Column(name = "flight_permit_valid_from")
  private Instant flightPermitValidFrom;

  @Column(name = "flight_permit_valid_to")
  private Instant flightPermitValidTo;

  @Column(name = "flight_permit_drone_serial", length = 120)
  private String flightPermitDroneSerial;

  @Column(name = "flight_permit_max_altitude_m")
  private Integer flightPermitMaxAltitudeM;

  @Column(name = "flight_permit_file_sha256", length = 64)
  private String flightPermitFileSha256;

  @Column(name = "flight_permit_file_size_bytes")
  private Long flightPermitFileSizeBytes;

  @Column(name = "flight_permit_content_type", length = 120)
  private String flightPermitContentType;

  @Column(name = "flight_permit_uploaded_at")
  private Instant flightPermitUploadedAt;

  @Column(name = "flight_permit_uploaded_by")
  private UUID flightPermitUploadedBy;

  @Enumerated(EnumType.STRING)
  @Column(name = "flight_permit_verification_status", nullable = false, length = 32)
  private FlightPermitVerificationStatus flightPermitVerificationStatus =
      FlightPermitVerificationStatus.NOT_UPLOADED;

  @Column(name = "flight_permit_verified_by")
  private UUID flightPermitVerifiedBy;

  @Column(name = "flight_permit_verified_at")
  private Instant flightPermitVerifiedAt;

  @Column(name = "flight_permit_rejection_reason", length = 1000)
  private String flightPermitRejectionReason;

  @Column(name = "flight_permit_review_notes", length = 1000)
  private String flightPermitReviewNotes;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private InspectionStatus status;

  @Enumerated(EnumType.STRING)
  @Column(name = "inspection_type", nullable = false, length = 32)
  private com.smartdroneinspection.inspections.domain.enums.InspectionType inspectionType =
      com.smartdroneinspection.inspections.domain.enums.InspectionType.INITIAL;

  @Column(name = "source_inspection_id")
  private UUID sourceInspectionId;

  @Column(name = "findings_confirmed_at")
  private Instant findingsConfirmedAt;

  @Column(name = "partner_assignment", nullable = false)
  private boolean partnerAssignment;

  @Column(name = "decline_reason", length = 2000)
  private String declineReason;

  @Column(name = "declined_at")
  private Instant declinedAt;

  @Column(name = "declined_by_inspector_id")
  private UUID declinedByInspectorId;

  @Column(name = "cancellation_reason", length = 2000)
  private String cancellationReason;

  @Column(name = "overdue_notified_at")
  private Instant overdueNotifiedAt;

  @Column(name = "clarification_count", nullable = false)
  private int clarificationCount;

  @Enumerated(EnumType.STRING)
  @Column(name = "assignment_status", nullable = false, length = 32)
  private AssignmentStatus assignmentStatus = AssignmentStatus.ACCEPTED;

  @Column(name = "assignment_accepted_at")
  private Instant assignmentAcceptedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "row_version", nullable = false)
  private long rowVersion;

  protected Inspection() {}

  public Inspection(
      UUID ownerCustomerId,
      UUID assetId,
      UUID inspectorId,
      UUID droneId,
      Instant plannedStartAt,
      Instant plannedEndAt) {
    this.ownerCustomerId =
        Objects.requireNonNull(ownerCustomerId, "Inspection owner customer is required");
    this.assetId = Objects.requireNonNull(assetId, "Inspection asset is required");
    this.inspectorId = inspectorId;
    this.droneId = droneId;
    if (plannedStartAt != null && plannedEndAt != null && !plannedEndAt.isAfter(plannedStartAt)) {
      throw new IllegalArgumentException("Planned end must be after planned start");
    }
    this.plannedStartAt = plannedStartAt;
    this.plannedEndAt = plannedEndAt;
    this.status = InspectionStatus.SCHEDULED;
    this.createdAt = Instant.now();
    this.updatedAt = createdAt;
  }

  public Inspection(UUID ownerCustomerId, UUID assetId, Instant plannedStartAt) {
    this(ownerCustomerId, assetId, null, null, plannedStartAt, null);
  }

  public static Inspection createReinspection(
      UUID ownerCustomerId,
      UUID assetId,
      UUID inspectorId,
      UUID droneId,
      Instant plannedStartAt,
      Instant plannedEndAt,
      UUID sourceInspectionId) {
    Inspection inspection =
        new Inspection(
            ownerCustomerId, assetId, inspectorId, droneId, plannedStartAt, plannedEndAt);
    inspection.inspectionType =
        com.smartdroneinspection.inspections.domain.enums.InspectionType.RE_INSPECTION;
    inspection.sourceInspectionId =
        Objects.requireNonNull(sourceInspectionId, "Source inspection ID is required");
    inspection.markCustomerOwned(ownerCustomerId);
    if (inspectorId != null && droneId != null) {
      inspection.partnerAssignment = true;
    }
    return inspection;
  }

  public UUID getSourceInspectionId() {
    return sourceInspectionId;
  }

  public boolean isReinspection() {
    return inspectionType
        == com.smartdroneinspection.inspections.domain.enums.InspectionType.RE_INSPECTION;
  }

  /** Marks an inspection assigned by the MF2 partner registry path. */
  public void markPartnerAssignment() {
    if (inspectorId == null || droneId == null || status != InspectionStatus.SCHEDULED) {
      throw new IllegalStateException("A partner inspection must be fully assigned and scheduled");
    }
    partnerAssignment = true;
  }

  public boolean isPartnerAssignment() {
    return partnerAssignment;
  }

  public void recordFlightPermit(String fileKey, String fileName, Instant uploadedAt) {
    recordFlightPermit(
        fileKey, fileName, null, null, null, null, null, null, null, null, null, uploadedAt, null);
  }

  public void recordFlightPermit(
      String fileKey,
      String fileName,
      String permitNumber,
      String issuingAuthority,
      Instant validFrom,
      Instant validTo,
      String droneSerial,
      Integer maxAltitudeM,
      String fileSha256,
      Long fileSizeBytes,
      String contentType,
      Instant uploadedAt,
      UUID uploadedBy) {
    if (fileKey == null || fileKey.isBlank()) {
      throw new IllegalArgumentException("Flight permit file key must not be blank");
    }
    if (fileName == null || fileName.isBlank()) {
      throw new IllegalArgumentException("Flight permit file name must not be blank");
    }
    if (validFrom != null && validTo != null && !validTo.isAfter(validFrom)) {
      throw new IllegalArgumentException("Flight permit expiration must be after start time");
    }
    if (maxAltitudeM != null && (maxAltitudeM <= 0 || maxAltitudeM > 2000)) {
      throw new IllegalArgumentException(
          "Flight permit max altitude must be between 1 and 2000 meters");
    }
    Instant effectiveUploadedAt = uploadedAt != null ? uploadedAt : Instant.now();
    this.flightPermitFileKey = fileKey.trim();
    this.flightPermitFileName = fileName.trim();
    this.flightPermitNumber =
        permitNumber == null || permitNumber.isBlank() ? null : permitNumber.trim();
    this.flightPermitAuthority =
        issuingAuthority == null || issuingAuthority.isBlank() ? null : issuingAuthority.trim();
    this.flightPermitValidFrom = validFrom;
    this.flightPermitValidTo = validTo;
    this.flightPermitDroneSerial =
        droneSerial == null || droneSerial.isBlank() ? null : droneSerial.trim();
    this.flightPermitMaxAltitudeM = maxAltitudeM;
    this.flightPermitFileSha256 =
        fileSha256 == null || fileSha256.isBlank() ? null : fileSha256.trim();
    this.flightPermitFileSizeBytes = fileSizeBytes;
    this.flightPermitContentType =
        contentType == null || contentType.isBlank() ? null : contentType.trim();
    this.flightPermitUploadedAt = effectiveUploadedAt;
    this.flightPermitUploadedBy = uploadedBy;
    this.flightPermitVerificationStatus = FlightPermitVerificationStatus.PENDING_REVIEW;
    this.flightPermitVerifiedBy = null;
    this.flightPermitVerifiedAt = null;
    this.flightPermitRejectionReason = null;
    this.flightPermitReviewNotes = null;
    this.updatedAt = effectiveUploadedAt;
  }

  public void approveFlightPermit(UUID reviewerId, String notes, Instant verifiedAt) {
    if (!hasFlightPermit()) {
      throw new IllegalStateException("Cannot approve a missing flight permit");
    }
    if (flightPermitVerificationStatus != FlightPermitVerificationStatus.PENDING_REVIEW) {
      throw new IllegalStateException(
          "Flight permit can only be approved from PENDING_REVIEW status");
    }
    Instant now = verifiedAt != null ? verifiedAt : Instant.now();
    this.flightPermitVerificationStatus = FlightPermitVerificationStatus.APPROVED;
    this.flightPermitVerifiedBy = Objects.requireNonNull(reviewerId, "Reviewer ID is required");
    this.flightPermitVerifiedAt = now;
    this.flightPermitRejectionReason = null;
    this.flightPermitReviewNotes = notes == null || notes.isBlank() ? null : notes.trim();
    this.updatedAt = now;
  }

  public void rejectFlightPermit(UUID reviewerId, String reason, String notes, Instant verifiedAt) {
    if (!hasFlightPermit()) {
      throw new IllegalStateException("Cannot reject a missing flight permit");
    }
    if (flightPermitVerificationStatus != FlightPermitVerificationStatus.PENDING_REVIEW
        && flightPermitVerificationStatus != FlightPermitVerificationStatus.APPROVED) {
      throw new IllegalStateException(
          "Flight permit can only be rejected from PENDING_REVIEW or APPROVED status");
    }
    if (reason == null || reason.isBlank()) {
      throw new IllegalArgumentException("Rejection reason is required");
    }
    Instant now = verifiedAt != null ? verifiedAt : Instant.now();
    this.flightPermitVerificationStatus = FlightPermitVerificationStatus.REJECTED;
    this.flightPermitVerifiedBy = Objects.requireNonNull(reviewerId, "Reviewer ID is required");
    this.flightPermitVerifiedAt = now;
    this.flightPermitRejectionReason = reason.trim();
    this.flightPermitReviewNotes = notes == null || notes.isBlank() ? null : notes.trim();
    this.updatedAt = now;
  }

  public boolean hasFlightPermit() {
    return flightPermitFileKey != null && !flightPermitFileKey.isBlank();
  }

  public boolean isFlightPermitApproved() {
    return hasFlightPermit()
        && flightPermitVerificationStatus == FlightPermitVerificationStatus.APPROVED;
  }

  /** MF3-06: the Inspector confirms the reviewed evidence/findings baseline before drafting. */
  public void confirmFindings() {
    if (status != InspectionStatus.FIELD_COMPLETED) {
      throw new IllegalStateException("Findings can only be confirmed after field completion");
    }
    findingsConfirmedAt = Instant.now();
    updatedAt = findingsConfirmedAt;
  }

  /** A pre-submission edit invalidates the reviewed set and returns the inspection to Findings. */
  public void reopenFindings() {
    findingsConfirmedAt = null;
    updatedAt = Instant.now();
  }

  public Instant getFindingsConfirmedAt() {
    return findingsConfirmedAt;
  }

  public boolean hasFindingsConfirmation() {
    return findingsConfirmedAt != null;
  }

  /** Deprecated transition retained for backwards compatibility. */
  @Deprecated
  public void beginReportDraft() {
    // In 6-status model, report drafting occurs while status is FIELD_COMPLETED
  }

  /** MF3-08: report submitted and awaiting reviewer decision. */
  public void markUnderReview() {
    if (status != InspectionStatus.FIELD_COMPLETED
        && status != InspectionStatus.UNDER_REVIEW
        && status != InspectionStatus.COMPLETED) {
      throw new IllegalStateException(
          "Inspection must be completed in the field, under review, or already completed but is "
              + status);
    }
    status = InspectionStatus.UNDER_REVIEW;
    updatedAt = Instant.now();
  }

  /** Report returned by reviewer to author for revisions. */
  public void returnToDraft() {
    if (status != InspectionStatus.UNDER_REVIEW && status != InspectionStatus.COMPLETED) {
      throw new IllegalStateException("Only an inspection under review can be returned to draft");
    }
    status = InspectionStatus.FIELD_COMPLETED;
    updatedAt = Instant.now();
  }

  /** MF3-11 publication. Concludes the inspection into COMPLETED state. */
  public void markReportPublished() {
    if (status != InspectionStatus.FIELD_COMPLETED
        && status != InspectionStatus.UNDER_REVIEW
        && status != InspectionStatus.COMPLETED) {
      throw new IllegalStateException(
          "Inspection must be completed in the field, under review, or already completed but is "
              + status);
    }
    status = InspectionStatus.COMPLETED;
    updatedAt = Instant.now();
  }

  /** Concludes the inspection. */
  public void complete() {
    if (status != InspectionStatus.UNDER_REVIEW
        && status != InspectionStatus.FIELD_COMPLETED
        && status != InspectionStatus.COMPLETED) {
      throw new IllegalStateException(
          "Only an under review or field completed inspection can be completed");
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

  public UUID getOwnerCustomerId() {
    return ownerCustomerId;
  }

  /** Phase 1 bridge: records the owning customer user alongside the legacy organization. */
  public void markCustomerOwned(UUID ownerCustomerId) {
    this.ownerCustomerId = ownerCustomerId;
    this.updatedAt = Instant.now();
  }

  public com.smartdroneinspection.inspections.domain.enums.InspectionType getInspectionType() {
    return inspectionType;
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

  public String getFlightPermitNumber() {
    return flightPermitNumber;
  }

  public String getFlightPermitAuthority() {
    return flightPermitAuthority;
  }

  public Instant getFlightPermitValidFrom() {
    return flightPermitValidFrom;
  }

  public Instant getFlightPermitValidTo() {
    return flightPermitValidTo;
  }

  public String getFlightPermitDroneSerial() {
    return flightPermitDroneSerial;
  }

  public Integer getFlightPermitMaxAltitudeM() {
    return flightPermitMaxAltitudeM;
  }

  public String getFlightPermitFileSha256() {
    return flightPermitFileSha256;
  }

  public Long getFlightPermitFileSizeBytes() {
    return flightPermitFileSizeBytes;
  }

  public String getFlightPermitContentType() {
    return flightPermitContentType;
  }

  public Instant getFlightPermitUploadedAt() {
    return flightPermitUploadedAt;
  }

  public UUID getFlightPermitUploadedBy() {
    return flightPermitUploadedBy;
  }

  public FlightPermitVerificationStatus getFlightPermitVerificationStatus() {
    return flightPermitVerificationStatus == null
        ? FlightPermitVerificationStatus.NOT_UPLOADED
        : flightPermitVerificationStatus;
  }

  public UUID getFlightPermitVerifiedBy() {
    return flightPermitVerifiedBy;
  }

  public Instant getFlightPermitVerifiedAt() {
    return flightPermitVerifiedAt;
  }

  public String getFlightPermitRejectionReason() {
    return flightPermitRejectionReason;
  }

  public String getFlightPermitReviewNotes() {
    return flightPermitReviewNotes;
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

  public void dispatch() {
    if (status != InspectionStatus.SCHEDULED) {
      throw new IllegalStateException("Only a scheduled inspection can be dispatched");
    }
  }

  public void completeFieldSession() {
    requireStatus(InspectionStatus.IN_PROGRESS);
    moveTo(InspectionStatus.FIELD_COMPLETED);
  }

  public boolean isReassignmentRequired() {
    return assignmentStatus == AssignmentStatus.DECLINED;
  }

  public String getDeclineReason() {
    return declineReason;
  }

  public Instant getDeclinedAt() {
    return declinedAt;
  }

  public UUID getDeclinedByInspectorId() {
    return declinedByInspectorId;
  }

  public String getCancellationReason() {
    return cancellationReason;
  }

  public Instant getOverdueNotifiedAt() {
    return overdueNotifiedAt;
  }

  public int getClarificationCount() {
    return clarificationCount;
  }

  public AssignmentStatus getAssignmentStatus() {
    return assignmentStatus;
  }

  public Instant getAssignmentAcceptedAt() {
    return assignmentAcceptedAt;
  }

  public boolean isAssignmentPendingAcceptance() {
    return assignmentStatus == AssignmentStatus.PENDING_ACCEPTANCE;
  }

  public void markPendingAcceptance() {
    this.assignmentStatus = AssignmentStatus.PENDING_ACCEPTANCE;
    this.assignmentAcceptedAt = null;
    this.updatedAt = Instant.now();
  }

  public void acceptAssignment(UUID actorId) {
    if (status != InspectionStatus.SCHEDULED) {
      throw new IllegalStateException("Only a scheduled inspection can be accepted");
    }
    if (assignmentStatus == AssignmentStatus.DECLINED) {
      throw new IllegalStateException(
          "This inspection was declined and awaits manager reassignment");
    }
    if (!isInspectedBy(actorId)) {
      throw new IllegalStateException("Only the assigned inspector may accept this assignment");
    }
    if (this.assignmentStatus == AssignmentStatus.ACCEPTED && this.assignmentAcceptedAt != null) {
      return;
    }
    Instant now = Instant.now();
    this.assignmentStatus = AssignmentStatus.ACCEPTED;
    this.assignmentAcceptedAt = now;
    this.updatedAt = now;
  }

  public void decline(UUID inspectorId, String reason) {
    if (status != InspectionStatus.SCHEDULED) {
      throw new IllegalStateException("Only a scheduled inspection can be declined");
    }
    if (assignmentStatus == AssignmentStatus.DECLINED) {
      throw new IllegalStateException("This inspection has already been declined for reassignment");
    }
    if (reason == null || reason.isBlank()) {
      throw new IllegalArgumentException("Decline reason is required");
    }
    this.assignmentStatus = AssignmentStatus.DECLINED;
    this.assignmentAcceptedAt = null;
    this.declineReason = reason.trim();
    this.declinedAt = Instant.now();
    this.declinedByInspectorId = Objects.requireNonNull(inspectorId, "Inspector ID is required");
    this.updatedAt = this.declinedAt;
  }

  public void markOverdueNotified(Instant at) {
    this.overdueNotifiedAt = Objects.requireNonNull(at, "Timestamp is required");
    this.updatedAt = Instant.now();
  }

  public void incrementClarificationCount() {
    this.clarificationCount += 1;
    this.updatedAt = Instant.now();
  }

  public void reassign(UUID newInspectorId, UUID newDroneId) {
    if (status != InspectionStatus.SCHEDULED) {
      throw new IllegalStateException("Only a scheduled inspection can be reassigned");
    }
    this.inspectorId = Objects.requireNonNull(newInspectorId, "Inspector ID is required");
    boolean droneChanged = !Objects.equals(this.droneId, newDroneId);
    this.droneId = newDroneId;
    if (droneChanged
        && hasFlightPermit()
        && this.flightPermitVerificationStatus == FlightPermitVerificationStatus.APPROVED) {
      resetFlightPermitVerificationForReReview();
    }
    this.assignmentStatus = AssignmentStatus.PENDING_ACCEPTANCE;
    this.assignmentAcceptedAt = null;
    this.declineReason = null;
    this.declinedAt = null;
    this.declinedByInspectorId = null;
    this.overdueNotifiedAt = null;
    this.updatedAt = Instant.now();
  }

  public void cancel(String reason) {
    if (status == InspectionStatus.COMPLETED || status == InspectionStatus.CANCELLED) {
      throw new IllegalStateException("Inspection cannot be cancelled from status " + status);
    }
    this.cancellationReason = reason == null || reason.isBlank() ? null : reason.trim();
    moveTo(InspectionStatus.CANCELLED);
  }

  public void updatePlannedWindow(Instant plannedStartAt, Instant plannedEndAt) {
    if (status != InspectionStatus.SCHEDULED) {
      throw new IllegalStateException("Only a scheduled inspection can be rescheduled");
    }
    if (plannedStartAt == null || plannedEndAt == null || !plannedEndAt.isAfter(plannedStartAt)) {
      throw new IllegalArgumentException("Planned end must be after planned start");
    }
    this.plannedStartAt = plannedStartAt;
    this.plannedEndAt = plannedEndAt;
    if (hasFlightPermit()
        && this.flightPermitVerificationStatus == FlightPermitVerificationStatus.APPROVED
        && ((this.flightPermitValidFrom != null
                && plannedStartAt.isBefore(this.flightPermitValidFrom))
            || (this.flightPermitValidTo != null
                && plannedEndAt.isAfter(this.flightPermitValidTo)))) {
      resetFlightPermitVerificationForReReview();
    }
    if (this.assignmentStatus == AssignmentStatus.DECLINED) {
      this.assignmentStatus = AssignmentStatus.PENDING_ACCEPTANCE;
      this.assignmentAcceptedAt = null;
      this.declineReason = null;
      this.declinedAt = null;
      this.declinedByInspectorId = null;
    }
    this.updatedAt = Instant.now();
  }

  private void resetFlightPermitVerificationForReReview() {
    this.flightPermitVerificationStatus = FlightPermitVerificationStatus.PENDING_REVIEW;
    this.flightPermitVerifiedBy = null;
    this.flightPermitVerifiedAt = null;
    this.flightPermitRejectionReason = null;
    this.flightPermitReviewNotes = null;
  }

  /**
   * Brings a future schedule or permit start forward to now for an explicit immediate start while
   * preserving approval status and expiration invariants.
   */
  public void bringForwardForImmediateStart(Instant now) {
    Instant effectiveNow = Objects.requireNonNull(now, "Timestamp is required");
    if (status != InspectionStatus.SCHEDULED && status != InspectionStatus.FIELD_COMPLETED) {
      throw new IllegalStateException(
          "Only a scheduled or field-completed inspection may start immediately");
    }
    if (!isFlightPermitApproved()) {
      throw new IllegalStateException("Cannot bring forward an unapproved flight permit");
    }
    if (this.flightPermitValidTo != null && !this.flightPermitValidTo.isAfter(effectiveNow)) {
      throw new IllegalStateException("Cannot start immediately with an expired flight permit");
    }
    if (this.plannedEndAt != null && !this.plannedEndAt.isAfter(effectiveNow)) {
      throw new IllegalStateException(
          "Cannot start immediately after the scheduled window has expired");
    }
    Instant shiftedStart = effectiveNow.minusSeconds(60);
    if (this.flightPermitValidFrom != null && effectiveNow.isBefore(this.flightPermitValidFrom)) {
      this.flightPermitValidFrom = shiftedStart;
    }
    if (this.plannedStartAt != null && effectiveNow.isBefore(this.plannedStartAt)) {
      this.plannedStartAt = shiftedStart;
    }
    this.updatedAt = effectiveNow;
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
