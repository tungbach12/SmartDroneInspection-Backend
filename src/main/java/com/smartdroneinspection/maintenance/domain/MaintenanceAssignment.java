package com.smartdroneinspection.maintenance.domain;

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
@Table(name = "maintenance_assignments")
public class MaintenanceAssignment {

  @Id @GeneratedValue private UUID id;

  @Column(name = "maintenance_ticket_id", nullable = false)
  private UUID maintenanceTicketId;

  @Column(name = "maintenance_order_id")
  private UUID maintenanceOrderId;

  @Column(name = "engineer_user_id", nullable = false)
  private UUID engineerUserId;

  @Column(name = "assigned_by_user_id", nullable = false)
  private UUID assignedByUserId;

  @Enumerated(EnumType.STRING)
  @Column(name = "assignment_type", nullable = false, length = 16)
  private MaintenanceAssignmentType assignmentType;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private MaintenanceAssignmentStatus status;

  @Column private Instant deadline;

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

  protected MaintenanceAssignment() {}

  public MaintenanceAssignment(
      UUID maintenanceTicketId,
      UUID maintenanceOrderId,
      UUID engineerUserId,
      UUID assignedByUserId,
      MaintenanceAssignmentType assignmentType,
      Instant deadline) {
    this.maintenanceTicketId = maintenanceTicketId;
    this.maintenanceOrderId = maintenanceOrderId;
    this.engineerUserId = engineerUserId;
    this.assignedByUserId = assignedByUserId;
    this.assignmentType = assignmentType;
    this.deadline = deadline;
    this.status = MaintenanceAssignmentStatus.PENDING;
    this.createdAt = Instant.now();
    this.updatedAt = createdAt;
  }

  public UUID getId() {
    return id;
  }

  public UUID getMaintenanceTicketId() {
    return maintenanceTicketId;
  }

  public UUID getMaintenanceOrderId() {
    return maintenanceOrderId;
  }

  public MaintenanceAssignmentType getAssignmentType() {
    return assignmentType;
  }

  public MaintenanceAssignmentStatus getStatus() {
    return status;
  }
}
