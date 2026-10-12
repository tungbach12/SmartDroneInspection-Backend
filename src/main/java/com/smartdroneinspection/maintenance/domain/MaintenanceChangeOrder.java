package com.smartdroneinspection.maintenance.domain;

import com.smartdroneinspection.maintenance.domain.enums.ChangeOrderStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A proposal to widen an approved work order's scope, cost or time.
 *
 * <p>MF4-12: additional work is unauthorized until approved. The original approved baseline is
 * never edited; an approved change contributes a delta to the authorized amount instead.
 */
@Entity
@Table(name = "maintenance_change_orders")
public class MaintenanceChangeOrder {

  @Id @GeneratedValue private UUID id;

  @Column(name = "work_order_id", nullable = false)
  private UUID workOrderId;

  @Column(name = "change_number", nullable = false)
  private int changeNumber;

  @Column(nullable = false, length = 2000)
  private String reason;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "affected_tasks", columnDefinition = "jsonb")
  private String affectedTasks;

  @Column(name = "proposed_delta", precision = 18, scale = 2)
  private BigDecimal proposedDelta;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "supporting_evidence", columnDefinition = "jsonb")
  private String supportingEvidence;

  @Column(name = "proposed_start_at")
  private Instant proposedStartAt;

  @Column(name = "proposed_end_at")
  private Instant proposedEndAt;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private ChangeOrderStatus status;

  @Column(name = "requested_by_user_id", nullable = false)
  private UUID requestedByUserId;

  @Column(name = "decided_by_user_id")
  private UUID decidedByUserId;

  @Column(name = "decided_at")
  private Instant decidedAt;

  @Column(name = "decision_reason", length = 2000)
  private String decisionReason;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "row_version", nullable = false)
  private long rowVersion;

  protected MaintenanceChangeOrder() {}

  public MaintenanceChangeOrder(
      UUID workOrderId,
      int changeNumber,
      String reason,
      String affectedTasks,
      BigDecimal proposedDelta,
      String supportingEvidence,
      Instant proposedStartAt,
      Instant proposedEndAt,
      UUID requestedByUserId) {
    this.workOrderId = require(workOrderId, "workOrderId");
    this.changeNumber = requirePositive(changeNumber, "changeNumber");
    this.reason = requireText(reason, "reason");
    this.affectedTasks = affectedTasks;
    this.proposedDelta = proposedDelta;
    this.supportingEvidence = supportingEvidence;
    this.proposedStartAt = proposedStartAt;
    this.proposedEndAt = proposedEndAt;
    this.requestedByUserId = require(requestedByUserId, "requestedByUserId");
    requireWindow(proposedStartAt, proposedEndAt);
    this.status = ChangeOrderStatus.DRAFT;
    this.createdAt = Instant.now();
    this.updatedAt = this.createdAt;
  }

  /** MF4-12: submitting sends the proposal for a decision; it does not widen anything yet. */
  public void submit() {
    requireStatus(ChangeOrderStatus.DRAFT, ChangeOrderStatus.RETURNED);
    this.status = ChangeOrderStatus.AWAITING_APPROVAL;
    this.updatedAt = Instant.now();
  }

  /** MF4-13: only an approved change may widen the authorized amount. */
  public void approve(UUID decidingUserId, String note) {
    requireStatus(ChangeOrderStatus.AWAITING_APPROVAL);
    this.decidedByUserId = require(decidingUserId, "decidingUserId");
    this.decidedAt = Instant.now();
    this.decisionReason = note;
    this.status = ChangeOrderStatus.APPROVED;
    this.updatedAt = Instant.now();
  }

  public void reject(UUID decidingUserId, String note) {
    requireStatus(ChangeOrderStatus.AWAITING_APPROVAL);
    if (note == null || note.isBlank()) {
      throw new IllegalArgumentException("A rejection requires a reason");
    }
    this.decidedByUserId = require(decidingUserId, "decidingUserId");
    this.decidedAt = Instant.now();
    this.decisionReason = note;
    this.status = ChangeOrderStatus.REJECTED;
    this.updatedAt = Instant.now();
  }

  /** MF4-13: returned asks the team for clarification without ending the request. */
  public void returnForClarification(UUID decidingUserId, String note) {
    requireStatus(ChangeOrderStatus.AWAITING_APPROVAL);
    if (note == null || note.isBlank()) {
      throw new IllegalArgumentException("A return requires a clarification request");
    }
    this.decidedByUserId = require(decidingUserId, "decidingUserId");
    this.decidedAt = Instant.now();
    this.decisionReason = note;
    this.status = ChangeOrderStatus.RETURNED;
    this.updatedAt = Instant.now();
  }

  public void supersede() {
    requireStatus(ChangeOrderStatus.APPROVED);
    this.status = ChangeOrderStatus.SUPERSEDED;
    this.updatedAt = Instant.now();
  }

  private void requireStatus(ChangeOrderStatus... allowed) {
    for (ChangeOrderStatus candidate : allowed) {
      if (status == candidate) {
        return;
      }
    }
    throw new IllegalStateException("Change order is " + status + "; expected one of " + allowed);
  }

  private static void requireWindow(Instant start, Instant end) {
    if (start != null && end != null && !end.isAfter(start)) {
      throw new IllegalArgumentException("proposedEndAt must be after proposedStartAt");
    }
  }

  private static UUID require(UUID value, String name) {
    if (value == null) {
      throw new IllegalArgumentException(name + " is required");
    }
    return value;
  }

  private static int requirePositive(int value, String name) {
    if (value <= 0) {
      throw new IllegalArgumentException(name + " must be positive");
    }
    return value;
  }

  private static String requireText(String value, String name) {
    if (value == null || value.isBlank()) {
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

  public int getChangeNumber() {
    return changeNumber;
  }

  public String getReason() {
    return reason;
  }

  public String getAffectedTasks() {
    return affectedTasks;
  }

  public BigDecimal getProposedDelta() {
    return proposedDelta;
  }

  public String getSupportingEvidence() {
    return supportingEvidence;
  }

  public Instant getProposedStartAt() {
    return proposedStartAt;
  }

  public Instant getProposedEndAt() {
    return proposedEndAt;
  }

  public ChangeOrderStatus getStatus() {
    return status;
  }

  public UUID getRequestedByUserId() {
    return requestedByUserId;
  }

  public UUID getDecidedByUserId() {
    return decidedByUserId;
  }

  public Instant getDecidedAt() {
    return decidedAt;
  }

  public String getDecisionReason() {
    return decisionReason;
  }
}
