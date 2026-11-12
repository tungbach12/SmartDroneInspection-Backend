package com.smartdroneinspection.contracts.domain;

import com.smartdroneinspection.contracts.domain.ServiceRequestOccurrence.OccurrencePayoutStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inspector_payouts")
public class InspectorPayout {

  @Id
  @Column(name = "occurrence_id")
  private UUID occurrenceId;

  @MapsId
  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "occurrence_id")
  private ServiceRequestOccurrence occurrence;

  @Column(name = "inspector_id")
  private UUID inspectorId;

  @Enumerated(EnumType.STRING)
  @Column(name = "payout_status", nullable = false, length = 24)
  private OccurrencePayoutStatus payoutStatus = OccurrencePayoutStatus.PENDING;

  @Column(name = "payout_reference", length = 255)
  private String payoutReference;

  @Column(name = "payout_recorded_at")
  private Instant payoutRecordedAt;

  @Column(name = "payout_held_for_refund", nullable = false)
  private boolean heldForRefund;

  @Column(name = "refund_reconciliation_reference", length = 255)
  private String refundReconciliationReference;

  @Column(name = "refund_reconciliation_note", length = 2000)
  private String refundReconciliationNote;

  @Column(name = "refund_reconciled_by_user_id")
  private UUID refundReconciledByUserId;

  @Column(name = "refund_reconciled_at")
  private Instant refundReconciledAt;

  @Column(name = "payout_recorded_by_user_id")
  private UUID payoutRecordedByUserId;

  @Column(name = "final_payout_amount", precision = 15, scale = 2)
  private java.math.BigDecimal finalPayoutAmount;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  @Version
  @Column(name = "row_version", nullable = false)
  private long rowVersion;

  protected InspectorPayout() {}

  InspectorPayout(ServiceRequestOccurrence occurrence) {
    this.occurrence = occurrence;
  }

  void assignInspector(UUID inspectorId) {
    this.inspectorId = inspectorId;
    updatedAt = Instant.now();
  }

  void markEligible() {
    if (payoutStatus == OccurrencePayoutStatus.PENDING) {
      payoutStatus = OccurrencePayoutStatus.ELIGIBLE;
      updatedAt = Instant.now();
    }
  }

  void holdForRefund() {
    if (heldForRefund) {
      throw new IllegalStateException("Existing refund allocation must be reconciled first");
    }
    heldForRefund = true;
    if (payoutStatus == OccurrencePayoutStatus.ELIGIBLE) {
      payoutStatus = OccurrencePayoutStatus.PENDING;
    }
    updatedAt = Instant.now();
  }

  void reconcileRefund(
      UUID managerId,
      java.math.BigDecimal finalAmount,
      java.math.BigDecimal originalNetAmount,
      String payoutReference,
      String reconciliationReference,
      String note) {
    if (!heldForRefund) {
      throw new IllegalStateException("Payout is not held for refund reconciliation");
    }
    if (payoutStatus == OccurrencePayoutStatus.PAID) {
      throw new IllegalStateException("A recorded payout cannot be reconciled a second time");
    }
    if (managerId == null
        || finalAmount == null
        || finalAmount.signum() < 0
        || originalNetAmount == null
        || finalAmount.compareTo(originalNetAmount) > 0
        || payoutReference == null
        || payoutReference.isBlank()
        || reconciliationReference == null
        || reconciliationReference.isBlank()
        || note == null
        || note.isBlank()) {
      throw new IllegalArgumentException(
          "Refund reconciliation requires a non-negative amount within the original net payout, payout reference, reconciliation reference, and note");
    }
    heldForRefund = false;
    refundReconciledByUserId = managerId;
    refundReconciliationReference = reconciliationReference.trim();
    refundReconciliationNote = note.trim();
    refundReconciledAt = Instant.now();
    finalPayoutAmount = finalAmount.setScale(2, java.math.RoundingMode.HALF_UP);
    this.payoutReference = payoutReference.trim();
    payoutRecordedByUserId = managerId;
    payoutRecordedAt = refundReconciledAt;
    payoutStatus = OccurrencePayoutStatus.PAID;
    updatedAt = refundReconciledAt;
  }

  void recordPayout(String reference, UUID managerId, java.math.BigDecimal originalNetAmount) {
    if (heldForRefund) {
      throw new IllegalStateException("Inspector payout is held pending refund reconciliation");
    }
    if (managerId == null) {
      throw new IllegalArgumentException("Payout recorder is required");
    }
    if (payoutStatus == OccurrencePayoutStatus.PAID) {
      if (reference.trim().equals(payoutReference)) {
        return;
      }
      throw new IllegalStateException(
          "Payout was already recorded with reference " + payoutReference);
    }
    payoutStatus = OccurrencePayoutStatus.PAID;
    finalPayoutAmount = originalNetAmount;
    payoutReference = reference.trim();
    payoutRecordedByUserId = managerId;
    payoutRecordedAt = Instant.now();
    updatedAt = payoutRecordedAt;
  }

  boolean isHeldForRefund() {
    return heldForRefund;
  }

  public boolean getHeldForRefund() {
    return heldForRefund;
  }

  public UUID getOccurrenceId() {
    return occurrenceId;
  }

  public UUID getInspectorId() {
    return inspectorId;
  }

  public OccurrencePayoutStatus getPayoutStatus() {
    return payoutStatus;
  }

  public String getPayoutReference() {
    return payoutReference;
  }

  public Instant getPayoutRecordedAt() {
    return payoutRecordedAt;
  }

  public java.math.BigDecimal getFinalPayoutAmount() {
    return finalPayoutAmount;
  }

  public UUID getPayoutRecordedByUserId() {
    return payoutRecordedByUserId;
  }

  public UUID getRefundReconciledByUserId() {
    return refundReconciledByUserId;
  }

  public String getRefundReconciliationReference() {
    return refundReconciliationReference;
  }

  public String getRefundReconciliationNote() {
    return refundReconciliationNote;
  }

  public Instant getRefundReconciledAt() {
    return refundReconciledAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
