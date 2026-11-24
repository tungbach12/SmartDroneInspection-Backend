package com.smartdroneinspection.inspections.domain;

import com.smartdroneinspection.inspections.domain.enums.AssignmentStatus;
import com.smartdroneinspection.inspections.domain.enums.FlightPermitVerificationStatus;
import com.smartdroneinspection.inspections.domain.enums.InspectionStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

  @OneToOne(
      mappedBy = "inspection",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.EAGER)
  private InspectionFlightPermit flightPermit;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private InspectionStatus status;

  @Enumerated(EnumType.STRING)
  @Column(name = "inspection_type", nullable = false, length = 32)
  private com.smartdroneinspection.inspections.domain.enums.InspectionType inspectionType =
      com.smartdroneinspection.inspections.domain.enums.InspectionType.INITIAL;

  @Column(name = "source_inspection_id")
  private UUID sourceInspectionId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "inspection_items", columnDefinition = "jsonb", nullable = false)
  private List<String> inspectionItems = new ArrayList<>();

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

  @Column(name = "assignment_requested_at")
  private Instant assignmentRequestedAt;

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
    if (this.flightPermit == null) {
      this.flightPermit = new InspectionFlightPermit(this);
    }
    this.flightPermit.record(
        fileKey,
        fileName,
        permitNumber,
        issuingAuthority,
        validFrom,
        validTo,
        droneSerial,
        maxAltitudeM,
        fileSha256,
        fileSizeBytes,
        contentType,
        effectiveUploadedAt,
        uploadedBy);
    this.updatedAt = effectiveUploadedAt;
  }

  public boolean hasFlightPermit() {
    return flightPermit != null && flightPermit.hasFile();
  }

  public boolean isFlightPermitApproved() {
    return flightPermit != null && flightPermit.isApproved();
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
    return flightPermit == null ? null : flightPermit.getFileKey();
  }

  public String getFlightPermitFileName() {
    return flightPermit == null ? null : flightPermit.getFileName();
  }

  public String getFlightPermitNumber() {
    return flightPermit == null ? null : flightPermit.getPermitNumber();
  }

  public String getFlightPermitAuthority() {
    return flightPermit == null ? null : flightPermit.getAuthority();
  }

  public Instant getFlightPermitValidFrom() {
    return flightPermit == null ? null : flightPermit.getValidFrom();
  }

  public Instant getFlightPermitValidTo() {
    return flightPermit == null ? null : flightPermit.getValidTo();
  }

  public String getFlightPermitDroneSerial() {
    return flightPermit == null ? null : flightPermit.getDroneSerial();
  }

  public Integer getFlightPermitMaxAltitudeM() {
    return flightPermit == null ? null : flightPermit.getMaxAltitudeM();
  }

  public String getFlightPermitFileSha256() {
    return flightPermit == null ? null : flightPermit.getFileSha256();
  }

  public Long getFlightPermitFileSizeBytes() {
    return flightPermit == null ? null : flightPermit.getFileSizeBytes();
  }

  public String getFlightPermitContentType() {
    return flightPermit == null ? null : flightPermit.getContentType();
  }

  public Instant getFlightPermitUploadedAt() {
    return flightPermit == null ? null : flightPermit.getUploadedAt();
  }

  public UUID getFlightPermitUploadedBy() {
    return flightPermit == null ? null : flightPermit.getUploadedBy();
  }

  public FlightPermitVerificationStatus getFlightPermitVerificationStatus() {
    return flightPermit == null
        ? FlightPermitVerificationStatus.NOT_UPLOADED
        : flightPermit.getVerificationStatus();
  }

  public UUID getFlightPermitVerifiedBy() {
    return flightPermit == null ? null : flightPermit.getVerifiedBy();
  }

  public Instant getFlightPermitVerifiedAt() {
    return flightPermit == null ? null : flightPermit.getVerifiedAt();
  }

  public String getFlightPermitRejectionReason() {
    return flightPermit == null ? null : flightPermit.getRejectionReason();
  }

  public String getFlightPermitReviewNotes() {
    return flightPermit == null ? null : flightPermit.getReviewNotes();
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

  public Instant getAssignmentRequestedAt() {
    return assignmentRequestedAt;
  }

  public boolean isAssignmentPendingAcceptance() {
    return assignmentStatus == AssignmentStatus.PENDING_ACCEPTANCE;
  }

  public void markPendingAcceptance() {
    Instant now = Instant.now();
    this.assignmentStatus = AssignmentStatus.PENDING_ACCEPTANCE;
    this.assignmentAcceptedAt = null;
    this.assignmentRequestedAt = now;
    this.updatedAt = now;
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
    if (droneChanged && isFlightPermitApproved()) {
      requireFlightPermitReupload();
    }
    Instant now = Instant.now();
    this.assignmentStatus = AssignmentStatus.PENDING_ACCEPTANCE;
    this.assignmentAcceptedAt = null;
    this.assignmentRequestedAt = now;
    this.declineReason = null;
    this.declinedAt = null;
    this.declinedByInspectorId = null;
    this.overdueNotifiedAt = null;
    this.updatedAt = now;
  }

  public boolean cancelForAcceptanceTimeout(int timeoutHours) {
    if (status != InspectionStatus.SCHEDULED
        || assignmentStatus != AssignmentStatus.PENDING_ACCEPTANCE) {
      return false;
    }
    String label = timeoutHours == 1 ? "1 hour" : timeoutHours + " hours";
    this.cancellationReason =
        "Auto-cancelled: Inspector did not accept assignment within " + label + ".";
    moveTo(InspectionStatus.CANCELLED);
    return true;
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
    Instant permitValidFrom = getFlightPermitValidFrom();
    Instant permitValidTo = getFlightPermitValidTo();
    if (isFlightPermitApproved()
        && ((permitValidFrom != null && plannedStartAt.isBefore(permitValidFrom))
            || (permitValidTo != null && plannedEndAt.isAfter(permitValidTo)))) {
      requireFlightPermitReupload();
    }
    if (this.assignmentStatus == AssignmentStatus.DECLINED) {
      this.assignmentStatus = AssignmentStatus.PENDING_ACCEPTANCE;
      this.assignmentAcceptedAt = null;
      this.assignmentRequestedAt = Instant.now();
      this.declineReason = null;
      this.declinedAt = null;
      this.declinedByInspectorId = null;
    }
    this.updatedAt = Instant.now();
  }

  private void requireFlightPermitReupload() {
    if (this.flightPermit != null) {
      this.flightPermit.requireReupload();
    }
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
    Instant permitValidTo = getFlightPermitValidTo();
    if (permitValidTo != null && !permitValidTo.isAfter(effectiveNow)) {
      throw new IllegalStateException("Cannot start immediately with an expired flight permit");
    }
    if (this.plannedEndAt != null && !this.plannedEndAt.isAfter(effectiveNow)) {
      throw new IllegalStateException(
          "Cannot start immediately after the scheduled window has expired");
    }
    Instant shiftedStart = effectiveNow.minusSeconds(60);
    Instant permitValidFrom = getFlightPermitValidFrom();
    if (permitValidFrom != null && effectiveNow.isBefore(permitValidFrom)) {
      this.flightPermit.shiftValidFrom(shiftedStart);
    }
    if (this.plannedStartAt != null && effectiveNow.isBefore(this.plannedStartAt)) {
      java.time.Duration originalDuration =
          this.plannedEndAt != null && this.plannedEndAt.isAfter(this.plannedStartAt)
              ? java.time.Duration.between(this.plannedStartAt, this.plannedEndAt)
              : null;
      this.plannedStartAt = shiftedStart;
      if (originalDuration != null) {
        this.plannedEndAt = shiftedStart.plus(originalDuration);
      }
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

  public List<String> getInspectionItems() {
    return inspectionItems == null ? List.of() : Collections.unmodifiableList(inspectionItems);
  }

  public void setInspectionItems(List<String> items) {
    if (items == null || items.isEmpty()) {
      this.inspectionItems = new ArrayList<>();
    } else {
      LinkedHashSet<String> cleaned = new LinkedHashSet<>();
      for (String item : items) {
        if (item == null) continue;
        String trimmed = item.trim();
        if (!trimmed.isEmpty()) {
          cleaned.add(trimmed);
        }
      }
      this.inspectionItems = new ArrayList<>(cleaned);
    }
    this.updatedAt = Instant.now();
  }

  public static List<String> resolveInspectionItems(
      Inspection inspection, List<String> assetFallback) {
    if (inspection != null) {
      List<String> own = inspection.getInspectionItems();
      if (own != null && !own.isEmpty()) {
        return own;
      }
    }
    return assetFallback == null ? List.of() : List.copyOf(assetFallback);
  }
}
