package com.smartdroneinspection.maintenance.domain;

import com.smartdroneinspection.maintenance.domain.enums.AcceptanceDecisionKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * An append-only record of the independent reviewer's decision on a completed work order.
 *
 * <p>MF4-18: completion is not acceptance. Only a reviewer outside the executing team may record a
 * decision, and cost reconciliation is a separate recorded decision rather than a side effect of
 * acceptance. Nothing is ever edited here; a corrected decision is a new row.
 */
@Entity
@Table(name = "maintenance_acceptance_decisions")
public class MaintenanceAcceptanceDecision {

  @Id @GeneratedValue private UUID id;

  @Column(name = "work_order_id", nullable = false)
  private UUID workOrderId;

  @Column(name = "report_version_id", nullable = false)
  private UUID reportVersionId;

  @Column(name = "reviewer_user_id", nullable = false)
  private UUID reviewerUserId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private AcceptanceDecisionKind decision;

  @Column(name = "technical_comments", length = 4000)
  private String technicalComments;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "acceptance_checklist", columnDefinition = "jsonb")
  private String acceptanceChecklist;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "test_result", columnDefinition = "jsonb")
  private String testResult;

  @Column(name = "decided_at", nullable = false, updatable = false)
  private Instant decidedAt;

  @Column(name = "signature_reference", length = 1000)
  private String signatureReference;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected MaintenanceAcceptanceDecision() {}

  public MaintenanceAcceptanceDecision(
      UUID workOrderId,
      UUID reportVersionId,
      UUID reviewerUserId,
      AcceptanceDecisionKind decision,
      String technicalComments,
      String acceptanceChecklist,
      String testResult,
      String signatureReference) {
    this.workOrderId = require(workOrderId, "workOrderId");
    this.reportVersionId = require(reportVersionId, "reportVersionId");
    this.reviewerUserId = require(reviewerUserId, "reviewerUserId");
    this.decision = require(decision, "decision");
    if (decision != AcceptanceDecisionKind.ACCEPTED && isBlank(technicalComments)) {
      throw new IllegalArgumentException(
          "Only an acceptance may be recorded without technical comments");
    }
    this.technicalComments = technicalComments;
    this.acceptanceChecklist = acceptanceChecklist;
    this.testResult = testResult;
    this.signatureReference = signatureReference;
    this.decidedAt = Instant.now();
    this.createdAt = this.decidedAt;
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private static UUID require(UUID value, String name) {
    if (value == null) {
      throw new IllegalArgumentException(name + " is required");
    }
    return value;
  }

  private static AcceptanceDecisionKind require(AcceptanceDecisionKind value, String name) {
    if (value == null) {
      throw new IllegalArgumentException(name + " is required");
    }
    return value;
  }

  public UUID getId() {
    return id;
  }

  public UUID getWorkOrderId() {
    return workOrderId;
  }

  public UUID getReportVersionId() {
    return reportVersionId;
  }

  public UUID getReviewerUserId() {
    return reviewerUserId;
  }

  public AcceptanceDecisionKind getDecision() {
    return decision;
  }

  public String getTechnicalComments() {
    return technicalComments;
  }

  public String getAcceptanceChecklist() {
    return acceptanceChecklist;
  }

  public String getTestResult() {
    return testResult;
  }

  public Instant getDecidedAt() {
    return decidedAt;
  }
}
