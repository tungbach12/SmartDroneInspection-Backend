package com.smartdroneinspection.maintenance.domain;

import com.smartdroneinspection.maintenance.domain.enums.ReinspectionRequestStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "maintenance_reinspection_requests")
public class ReinspectionRequest {

  @Id @GeneratedValue private UUID id;

  @Column(name = "organization_id", nullable = false)
  private UUID organizationId;

  @Column(name = "work_order_id", nullable = false)
  private UUID workOrderId;

  @Column(name = "maintenance_report_version_id", nullable = false)
  private UUID maintenanceReportVersionId;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "finding_ids", nullable = false, columnDefinition = "uuid[]")
  private UUID[] findingIds;

  @Column(name = "requested_by_user_id", nullable = false)
  private UUID requestedByUserId;

  @Column(name = "request_note", length = 4000)
  private String requestNote;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private ReinspectionRequestStatus status;

  @Column(name = "manager_user_id")
  private UUID managerUserId;

  @Column(name = "inspector_user_id")
  private UUID inspectorUserId;

  @Column(name = "drone_id")
  private UUID droneId;

  @Column(name = "inspection_id", unique = true)
  private UUID inspectionId;

  @Column(name = "inspection_report_id")
  private UUID inspectionReportId;

  @Column(name = "customer_decision", length = 32)
  private String customerDecision;

  @Column(name = "customer_decision_note", length = 4000)
  private String customerDecisionNote;

  @Column(name = "requested_at", nullable = false, updatable = false)
  private Instant requestedAt;

  @Column(name = "assigned_at")
  private Instant assignedAt;

  @Column(name = "decided_at")
  private Instant decidedAt;

  @Version
  @Column(name = "row_version", nullable = false)
  private long rowVersion;

  protected ReinspectionRequest() {}

  public ReinspectionRequest(
      UUID organizationId,
      UUID workOrderId,
      UUID maintenanceReportVersionId,
      UUID requestedByUserId,
      List<UUID> findingIds,
      String requestNote) {
    this.organizationId = require(organizationId, "organizationId");
    this.workOrderId = require(workOrderId, "workOrderId");
    this.maintenanceReportVersionId =
        require(maintenanceReportVersionId, "maintenanceReportVersionId");
    this.requestedByUserId = require(requestedByUserId, "requestedByUserId");
    if (findingIds == null
        || findingIds.isEmpty()
        || findingIds.stream().distinct().count() != findingIds.size()) {
      throw new IllegalArgumentException("Select one or more unique findings");
    }
    this.findingIds = findingIds.toArray(UUID[]::new);
    this.requestNote = normalize(requestNote);
    this.status = ReinspectionRequestStatus.REQUESTED;
    this.requestedAt = Instant.now();
  }

  public void assign(UUID managerUserId, UUID inspectorUserId, UUID droneId, UUID inspectionId) {
    requireStatus(ReinspectionRequestStatus.REQUESTED);
    this.managerUserId = require(managerUserId, "managerUserId");
    this.inspectorUserId = require(inspectorUserId, "inspectorUserId");
    this.droneId = require(droneId, "droneId");
    this.inspectionId = require(inspectionId, "inspectionId");
    this.assignedAt = Instant.now();
    this.status = ReinspectionRequestStatus.ASSIGNED;
  }

  public void markInspectionPublished(UUID inspectionReportId) {
    requireStatus(ReinspectionRequestStatus.ASSIGNED);
    this.inspectionReportId = require(inspectionReportId, "inspectionReportId");
    this.status = ReinspectionRequestStatus.INSPECTION_PUBLISHED;
  }

  public void decide(UUID customerId, String decision, String note) {
    if (status != ReinspectionRequestStatus.INSPECTION_PUBLISHED
        && status != ReinspectionRequestStatus.RETURNED) {
      throw new IllegalStateException(
          "A published reinspection report is required before decision");
    }
    if (!requestedByUserId.equals(customerId)) {
      throw new IllegalStateException("Only the requesting customer may decide this request");
    }
    if (!"ACCEPTED".equals(decision) && !"REPAIR_REQUIRED".equals(decision)) {
      throw new IllegalArgumentException("Decision must be ACCEPTED or REPAIR_REQUIRED");
    }
    if ("REPAIR_REQUIRED".equals(decision) && (note == null || note.isBlank())) {
      throw new IllegalArgumentException("A repair decision requires a reason");
    }
    this.customerDecision = decision;
    this.customerDecisionNote = normalize(note);
    this.decidedAt = Instant.now();
    this.status =
        "ACCEPTED".equals(decision)
            ? ReinspectionRequestStatus.ACCEPTED
            : ReinspectionRequestStatus.RETURNED;
  }

  private void requireStatus(ReinspectionRequestStatus expected) {
    if (status != expected) {
      throw new IllegalStateException("Request is " + status + "; expected " + expected);
    }
  }

  private static UUID require(UUID value, String field) {
    if (value == null) throw new IllegalArgumentException(field + " is required");
    return value;
  }

  private static String normalize(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  public UUID getId() {
    return id;
  }

  public UUID getOrganizationId() {
    return organizationId;
  }

  public UUID getWorkOrderId() {
    return workOrderId;
  }

  public UUID getMaintenanceReportVersionId() {
    return maintenanceReportVersionId;
  }

  public List<UUID> getFindingIds() {
    return List.of(findingIds);
  }

  public UUID getRequestedByUserId() {
    return requestedByUserId;
  }

  public String getRequestNote() {
    return requestNote;
  }

  public ReinspectionRequestStatus getStatus() {
    return status;
  }

  public UUID getManagerUserId() {
    return managerUserId;
  }

  public UUID getInspectorUserId() {
    return inspectorUserId;
  }

  public UUID getDroneId() {
    return droneId;
  }

  public UUID getInspectionId() {
    return inspectionId;
  }

  public UUID getInspectionReportId() {
    return inspectionReportId;
  }

  public String getCustomerDecision() {
    return customerDecision;
  }

  public String getCustomerDecisionNote() {
    return customerDecisionNote;
  }

  public Instant getRequestedAt() {
    return requestedAt;
  }

  public Instant getAssignedAt() {
    return assignedAt;
  }

  public Instant getDecidedAt() {
    return decidedAt;
  }
}
