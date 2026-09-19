package com.smartdroneinspection.inspectionrequests.domain;

import com.smartdroneinspection.inspectionrequests.domain.enums.InspectionAssignmentStatus;
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
@Table(name = "inspection_assignments")
public class InspectionAssignment {

  @Id @GeneratedValue private UUID id;

  @Column(name = "service_order_id", nullable = false)
  private UUID serviceOrderId;

  @Column(name = "inspector_user_id", nullable = false)
  private UUID inspectorUserId;

  @Column(name = "assigned_by_user_id", nullable = false)
  private UUID assignedByUserId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private InspectionAssignmentStatus status;

  @Column(name = "deadline")
  private Instant deadline;

  @Column(name = "access_instructions", length = 2000)
  private String accessInstructions;

  @Column(name = "responded_at")
  private Instant respondedAt;

  @Column(name = "rejection_reason", length = 1000)
  private String rejectionReason;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "row_version", nullable = false)
  private long rowVersion;

  protected InspectionAssignment() {}

  public InspectionAssignment(
      UUID serviceOrderId,
      UUID inspectorUserId,
      UUID assignedByUserId,
      Instant deadline,
      String accessInstructions) {
    this.serviceOrderId = Objects.requireNonNull(serviceOrderId, "Service order is required");
    this.inspectorUserId = Objects.requireNonNull(inspectorUserId, "Inspector is required");
    this.assignedByUserId = Objects.requireNonNull(assignedByUserId, "Assigner is required");
    this.deadline = deadline;
    this.accessInstructions = accessInstructions;
    this.status = InspectionAssignmentStatus.PENDING;
    this.createdAt = Instant.now();
    this.updatedAt = createdAt;
  }

  public void accept() {
    if (status != InspectionAssignmentStatus.PENDING) {
      throw new IllegalStateException("Only pending assignments can be accepted");
    }
    status = InspectionAssignmentStatus.ACCEPTED;
    respondedAt = Instant.now();
    touch();
  }

  public void reject(String reason) {
    if (status != InspectionAssignmentStatus.PENDING) {
      throw new IllegalStateException("Only pending assignments can be rejected");
    }
    if (reason == null || reason.isBlank()) {
      throw new IllegalArgumentException("Rejection reason must not be blank");
    }
    status = InspectionAssignmentStatus.REJECTED;
    rejectionReason = reason;
    respondedAt = Instant.now();
    touch();
  }

  public void cancel() {
    if (status != InspectionAssignmentStatus.PENDING
        && status != InspectionAssignmentStatus.ACCEPTED) {
      throw new IllegalStateException("Only pending or accepted assignments can be cancelled");
    }
    status = InspectionAssignmentStatus.CANCELLED;
    touch();
  }

  public UUID getId() {
    return id;
  }

  public UUID getServiceOrderId() {
    return serviceOrderId;
  }

  public UUID getInspectorUserId() {
    return inspectorUserId;
  }

  public UUID getAssignedByUserId() {
    return assignedByUserId;
  }

  public InspectionAssignmentStatus getStatus() {
    return status;
  }

  public Instant getDeadline() {
    return deadline;
  }

  public Instant getRespondedAt() {
    return respondedAt;
  }

  public String getRejectionReason() {
    return rejectionReason;
  }

  private void touch() {
    updatedAt = Instant.now();
  }
}
