package com.smartdroneinspection.assets.domain;

import com.smartdroneinspection.assets.domain.enums.AssetPairAssignmentStatus;
import com.smartdroneinspection.assets.domain.enums.AssignmentResponse;
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

/**
 * The inspector-drone pairing an organization assigned to one asset (MF1-09).
 *
 * <p>This is the record MF2-01 answers: the inspector opens the pairing assigned to them and
 * accepts or declines it. The response is stored beside the pairing rather than folded into {@code
 * reason}, so a later reader can tell an administrator's reason for pairing from the inspector's
 * reason for declining.
 */
@Entity
@Table(name = "asset_pair_assignments")
public class AssetPairAssignment {

  @Id @GeneratedValue private UUID id;

  @Column(name = "organization_id", nullable = false)
  private UUID organizationId;

  @Column(name = "asset_id", nullable = false)
  private UUID assetId;

  @Column(name = "inspector_user_id", nullable = false)
  private UUID inspectorUserId;

  @Column(name = "drone_id", nullable = false)
  private UUID droneId;

  @Column(name = "valid_from", nullable = false)
  private Instant validFrom;

  @Column(name = "valid_until")
  private Instant validUntil;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private AssetPairAssignmentStatus status;

  @Column(length = 2000)
  private String reason;

  @Column(name = "assigned_by_user_id", nullable = false)
  private UUID assignedByUserId;

  @Column(name = "assigned_at", nullable = false)
  private Instant assignedAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "assignment_response", length = 24)
  private AssignmentResponse assignmentResponse;

  @Column(name = "responded_at")
  private Instant respondedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "row_version", nullable = false)
  private long rowVersion;

  protected AssetPairAssignment() {}

  public AssetPairAssignment(
      UUID organizationId,
      UUID assetId,
      UUID inspectorUserId,
      UUID droneId,
      Instant validFrom,
      UUID assignedByUserId) {
    this.organizationId =
        Objects.requireNonNull(organizationId, "Pairing organization is required");
    this.assetId = Objects.requireNonNull(assetId, "Pairing asset is required");
    this.inspectorUserId =
        Objects.requireNonNull(inspectorUserId, "Assigned inspector is required");
    this.droneId = Objects.requireNonNull(droneId, "Assigned drone is required");
    this.assignedByUserId = Objects.requireNonNull(assignedByUserId, "Assigning user is required");
    if (validFrom == null) {
      throw new IllegalArgumentException("Pairing validity must start");
    }
    this.validFrom = validFrom;
    this.status = AssetPairAssignmentStatus.DRAFT;
    this.assignedAt = Instant.now();
    this.createdAt = assignedAt;
    this.updatedAt = assignedAt;
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

  public UUID getInspectorUserId() {
    return inspectorUserId;
  }

  public UUID getDroneId() {
    return droneId;
  }

  public Instant getValidFrom() {
    return validFrom;
  }

  public Instant getValidUntil() {
    return validUntil;
  }

  public AssetPairAssignmentStatus getStatus() {
    return status;
  }

  public String getReason() {
    return reason;
  }

  public UUID getAssignedByUserId() {
    return assignedByUserId;
  }

  public Instant getAssignedAt() {
    return assignedAt;
  }

  public AssignmentResponse getAssignmentResponse() {
    return assignmentResponse;
  }

  public Instant getRespondedAt() {
    return respondedAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public boolean hasResponded() {
    return assignmentResponse != null;
  }

  public boolean isActiveAt(Instant instant) {
    if (status != AssetPairAssignmentStatus.ACTIVE) {
      return false;
    }
    if (instant.isBefore(validFrom)) {
      return false;
    }
    return validUntil == null || !instant.isAfter(validUntil);
  }

  public void activate(String activationReason, Instant validUntilAt) {
    if (status != AssetPairAssignmentStatus.DRAFT) {
      throw new IllegalStateException("Only a draft pairing can be activated");
    }
    if (validUntilAt != null && !validUntilAt.isAfter(validFrom)) {
      throw new IllegalArgumentException("Pairing validity must end after it starts");
    }
    this.status = AssetPairAssignmentStatus.ACTIVE;
    this.reason = activationReason;
    this.validUntil = validUntilAt;
    this.updatedAt = Instant.now();
  }

  /**
   * Records the assigned inspector's answer (MF2-01). Only the paired inspector may answer, and
   * only once.
   *
   * @param respondingInspectorId the caller; must be the inspector this pairing names
   * @param response accepted or declined
   * @param rejectionReason required when declining
   */
  public void respond(
      UUID respondingInspectorId, AssignmentResponse response, String rejectionReason) {
    Objects.requireNonNull(response, "Assignment response is required");
    if (!inspectorUserId.equals(respondingInspectorId)) {
      throw new IllegalStateException("Only the assigned inspector may respond to this pairing");
    }
    if (assignmentResponse != null) {
      throw new IllegalStateException("This pairing has already received a response");
    }
    if (response == AssignmentResponse.REJECTED
        && (rejectionReason == null || rejectionReason.isBlank())) {
      throw new IllegalArgumentException("Declining a pairing requires a reason");
    }
    this.assignmentResponse = response;
    this.reason = rejectionReason;
    this.respondedAt = Instant.now();
    this.updatedAt = respondedAt;
  }

  public void suspend(String suspensionReason) {
    if (status == AssetPairAssignmentStatus.SUPERSEDED) {
      throw new IllegalStateException("A superseded pairing cannot be suspended");
    }
    this.status = AssetPairAssignmentStatus.SUSPENDED;
    this.reason = suspensionReason;
    this.updatedAt = Instant.now();
  }

  public void supersede(String supersedeReason) {
    if (status == AssetPairAssignmentStatus.SUPERSEDED) {
      throw new IllegalStateException("This pairing is already superseded");
    }
    this.status = AssetPairAssignmentStatus.SUPERSEDED;
    if (supersedeReason != null) {
      this.reason = supersedeReason;
    }
    this.updatedAt = Instant.now();
  }
}
