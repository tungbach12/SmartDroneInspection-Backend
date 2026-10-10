package com.smartdroneinspection.maintenance.domain;

import com.smartdroneinspection.maintenance.domain.enums.MaintenancePriority;
import com.smartdroneinspection.maintenance.domain.enums.WorkOrderStatus;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A repair work order raised from one confirmed repair-required finding on a published report.
 *
 * <p>The lifecycle implemented here is DRAFT -> AWAITING_APPROVAL -> APPROVED, plus APPROVED ->
 * REWORK_REQUIRED -> AWAITING_APPROVAL. Later slices continue from APPROVED.
 *
 * <p>Separation of duties is enforced in this class rather than only in the service: the accepting
 * reviewer must sit outside the executing team, and the budget approver must not be part of it
 * either. Report 3 §3.8.1 makes both absolute.
 */
@Entity
@Table(name = "maintenance_work_orders")
public class MaintenanceWorkOrder {

  @Id @GeneratedValue private UUID id;

  @Column(name = "organization_id", nullable = false)
  private UUID organizationId;

  @Column(name = "asset_id", nullable = false)
  private UUID assetId;

  @Column(name = "source_report_version_id", nullable = false)
  private UUID sourceReportVersionId;

  @Column(name = "source_finding_id", nullable = false)
  private UUID sourceFindingId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private WorkOrderStatus status;

  @Enumerated(EnumType.STRING)
  @Column(length = 24)
  private MaintenancePriority priority;

  @Column(name = "due_at")
  private Instant dueAt;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "corrective_scope", columnDefinition = "jsonb")
  private String correctiveScope;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "acceptance_criteria", columnDefinition = "jsonb")
  private String acceptanceCriteria;

  @Column(name = "owner_user_id", nullable = false)
  private UUID ownerUserId;

  @Column(name = "budget_approver_user_id", nullable = false)
  private UUID budgetApproverUserId;

  @Column(name = "team_lead_user_id")
  private UUID teamLeadUserId;

  @Column(name = "report_author_user_id")
  private UUID reportAuthorUserId;

  @Column(name = "accepting_reviewer_user_id")
  private UUID acceptingReviewerUserId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "row_version", nullable = false)
  private long rowVersion;

  protected MaintenanceWorkOrder() {}

  public MaintenanceWorkOrder(
      UUID organizationId,
      UUID assetId,
      UUID sourceReportVersionId,
      UUID sourceFindingId,
      UUID ownerUserId,
      UUID budgetApproverUserId) {
    this.organizationId = require(organizationId, "organizationId");
    this.assetId = require(assetId, "assetId");
    this.sourceReportVersionId = require(sourceReportVersionId, "sourceReportVersionId");
    this.sourceFindingId = require(sourceFindingId, "sourceFindingId");
    this.ownerUserId = require(ownerUserId, "ownerUserId");
    this.budgetApproverUserId = require(budgetApproverUserId, "budgetApproverUserId");
    this.status = WorkOrderStatus.DRAFT;
    this.createdAt = Instant.now();
    this.updatedAt = this.createdAt;
  }

  /** MF4-02: triage stays with the draft; once approval is requested the scope is being decided. */
  public void triage(
      MaintenancePriority priority,
      Instant dueAt,
      String correctiveScope,
      String acceptanceCriteria) {
    if (status != WorkOrderStatus.DRAFT) {
      throw new IllegalStateException(
          "Work order is " + status + "; triage is only editable while DRAFT");
    }
    this.priority = priority;
    this.dueAt = dueAt;
    this.correctiveScope = correctiveScope;
    this.acceptanceCriteria = acceptanceCriteria;
    this.updatedAt = Instant.now();
  }

  /**
   * MF4-03/04: names the lead, the report author and the independent accepting reviewer.
   *
   * <p>Lead and report author may be the same engineer; the accepting reviewer may not be anyone in
   * the executing team.
   */
  public void assignTeam(UUID leadUserId, UUID reportAuthorUserId, UUID acceptingReviewerUserId) {
    if (status != WorkOrderStatus.DRAFT) {
      throw new IllegalStateException(
          "Work order is " + status + "; the team can only be designated while DRAFT");
    }
    UUID lead = require(leadUserId, "leadUserId");
    UUID author = require(reportAuthorUserId, "reportAuthorUserId");
    UUID reviewer = require(acceptingReviewerUserId, "acceptingReviewerUserId");
    if (reviewer.equals(lead) || reviewer.equals(author)) {
      throw new IllegalStateException(
          "The accepting reviewer must be independent of the executing team");
    }
    this.teamLeadUserId = lead;
    this.reportAuthorUserId = author;
    this.acceptingReviewerUserId = reviewer;
    this.updatedAt = Instant.now();
  }

  /** MF4-08: approval cannot be requested before a complete, independent team exists. */
  public void submitForApproval() {
    if (teamLeadUserId == null || reportAuthorUserId == null || acceptingReviewerUserId == null) {
      throw new IllegalStateException(
          "A team of one lead, one report author and one independent reviewer is required");
    }
    if (status != WorkOrderStatus.DRAFT && status != WorkOrderStatus.REWORK_REQUIRED) {
      throw new IllegalStateException("Work order is " + status + "; approval cannot be requested");
    }
    this.status = WorkOrderStatus.AWAITING_APPROVAL;
    this.updatedAt = Instant.now();
  }

  /**
   * MF4-08: only the designated budget approver may approve, and they must sit outside the
   * executing team that prepared the estimate.
   */
  public void approveEstimate(UUID approvingUserId) {
    requireStatus(WorkOrderStatus.AWAITING_APPROVAL);
    UUID approver = require(approvingUserId, "approvingUserId");
    if (!approver.equals(budgetApproverUserId)) {
      throw new IllegalStateException(
          "Only the designated budget approver may approve this work order");
    }
    if (approver.equals(teamLeadUserId)
        || approver.equals(reportAuthorUserId)
        || approver.equals(acceptingReviewerUserId)) {
      throw new IllegalStateException("The budget approver must not be part of the executing team");
    }
    this.status = WorkOrderStatus.APPROVED;
    this.updatedAt = Instant.now();
  }

  public void requestRework(String reason) {
    requireStatus(WorkOrderStatus.APPROVED);
    if (reason == null || reason.isBlank()) {
      throw new IllegalArgumentException("A rework request requires a reason");
    }
    this.status = WorkOrderStatus.REWORK_REQUIRED;
    this.updatedAt = Instant.now();
  }

  private void requireStatus(WorkOrderStatus... allowed) {
    for (WorkOrderStatus candidate : allowed) {
      if (status == candidate) {
        return;
      }
    }
    throw new IllegalStateException("Work order is " + status + "; expected one of " + allowed);
  }

  private static UUID require(UUID value, String name) {
    if (value == null) {
      throw new IllegalArgumentException(name + " is required");
    }
    return value;
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

  public UUID getSourceReportVersionId() {
    return sourceReportVersionId;
  }

  public UUID getSourceFindingId() {
    return sourceFindingId;
  }

  public WorkOrderStatus getStatus() {
    return status;
  }

  public MaintenancePriority getPriority() {
    return priority;
  }

  public Instant getDueAt() {
    return dueAt;
  }

  public String getCorrectiveScope() {
    return correctiveScope;
  }

  public String getAcceptanceCriteria() {
    return acceptanceCriteria;
  }

  public UUID getOwnerUserId() {
    return ownerUserId;
  }

  public UUID getBudgetApproverUserId() {
    return budgetApproverUserId;
  }

  public UUID getTeamLeadUserId() {
    return teamLeadUserId;
  }

  public UUID getReportAuthorUserId() {
    return reportAuthorUserId;
  }

  public UUID getAcceptingReviewerUserId() {
    return acceptingReviewerUserId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
