package com.smartdroneinspection.contracts.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Attributable record of a refund already transferred outside the payment gateway. */
@Entity
@Table(name = "service_refunds")
public class ServiceRefund {

  public enum RefundStatus {
    INITIATED,
    COMPLETED,
    CANCELLED
  }

  @Id @GeneratedValue private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "occurrence_id", nullable = false)
  private ServicePayment payment;

  @Column(name = "amount", nullable = false, precision = 15, scale = 2)
  private BigDecimal amount;

  @Column(name = "inspector_receivable_amount", nullable = false, precision = 15, scale = 2)
  private BigDecimal inspectorReceivableAmount = BigDecimal.ZERO;

  @Column(name = "platform_absorbed_plan_amount", nullable = false, precision = 15, scale = 2)
  private BigDecimal platformAbsorbedPlanAmount = BigDecimal.ZERO;

  @Enumerated(jakarta.persistence.EnumType.STRING)
  @Column(name = "refund_status", nullable = false, length = 24)
  private RefundStatus status = RefundStatus.COMPLETED;

  @Column(name = "request_reference", length = 255)
  private String requestReference;

  @Column(name = "initiated_by_user_id")
  private UUID initiatedByUserId;

  @Column(name = "initiated_at")
  private Instant initiatedAt;

  @Column(name = "completed_by_user_id")
  private UUID completedByUserId;

  @Column(name = "completed_at")
  private Instant completedAt;

  @Column(name = "cancelled_by_user_id")
  private UUID cancelledByUserId;

  @Column(name = "cancelled_at")
  private Instant cancelledAt;

  @Column(name = "cancellation_reason", length = 2000)
  private String cancellationReason;

  @Column(name = "inspector_payout_reduction", precision = 15, scale = 2)
  private BigDecimal inspectorPayoutReduction;

  @Column(name = "platform_commission_adjustment", precision = 15, scale = 2)
  private BigDecimal platformCommissionAdjustment;

  @Column(name = "vat_adjustment", precision = 15, scale = 2)
  private BigDecimal vatAdjustment;

  @Column(name = "tncn_adjustment", precision = 15, scale = 2)
  private BigDecimal tncnAdjustment;

  @Column(name = "platform_absorbed_amount", precision = 15, scale = 2)
  private BigDecimal platformAbsorbedAmount;

  @Column(name = "refund_reference", length = 255)
  private String reference;

  @Column(name = "reason", nullable = false, length = 2000)
  private String reason;

  @Column(name = "recorded_by_user_id", nullable = false)
  private UUID recordedByUserId;

  @Column(name = "recorded_at", nullable = false, updatable = false)
  private Instant recordedAt = Instant.now();

  @Column(name = "allocation_reconciled_by_user_id")
  private UUID allocationReconciledByUserId;

  @Column(name = "allocation_reconciliation_reference", length = 255)
  private String allocationReconciliationReference;

  @Column(name = "allocation_reconciliation_note", length = 2000)
  private String allocationReconciliationNote;

  @Column(name = "allocation_reconciled_at")
  private Instant allocationReconciledAt;

  protected ServiceRefund() {}

  ServiceRefund(
      ServicePayment payment,
      BigDecimal amount,
      String requestReference,
      String reason,
      BigDecimal inspectorReceivableAmount,
      BigDecimal platformAbsorbedPlanAmount,
      UUID actorId) {
    this.payment = payment;
    this.amount = amount.setScale(2, java.math.RoundingMode.HALF_UP);
    this.requestReference = requestReference.trim();
    this.reason = reason.trim();
    this.inspectorReceivableAmount =
        inspectorReceivableAmount.setScale(2, java.math.RoundingMode.HALF_UP);
    this.platformAbsorbedPlanAmount =
        platformAbsorbedPlanAmount.setScale(2, java.math.RoundingMode.HALF_UP);
    this.status = RefundStatus.INITIATED;
    this.initiatedByUserId = actorId;
    this.initiatedAt = Instant.now();
    this.recordedByUserId = actorId;
  }

  public void completeExternalTransfer(
      BigDecimal inspectorPayoutReduction,
      BigDecimal platformCommissionAdjustment,
      BigDecimal vatAdjustment,
      BigDecimal tncnAdjustment,
      BigDecimal platformAbsorbedAmount,
      String externalReference,
      UUID actorId) {
    if (status != RefundStatus.INITIATED) {
      throw new IllegalStateException("Only an initiated refund can be completed");
    }
    if (actorId == null || externalReference == null || externalReference.isBlank()) {
      throw new IllegalArgumentException(
          "Refund transfer actor and external reference are required");
    }
    this.inspectorPayoutReduction =
        requireNonNegative(inspectorPayoutReduction, "Inspector payout reduction");
    this.platformCommissionAdjustment =
        requireNonNegative(platformCommissionAdjustment, "Platform commission adjustment");
    this.vatAdjustment = requireNonNegative(vatAdjustment, "VAT adjustment");
    this.tncnAdjustment = requireNonNegative(tncnAdjustment, "TNCN adjustment");
    this.platformAbsorbedAmount =
        requireNonNegative(platformAbsorbedAmount, "Platform absorbed amount");
    BigDecimal allocated =
        this.inspectorPayoutReduction
            .add(this.platformCommissionAdjustment)
            .add(this.vatAdjustment)
            .add(this.tncnAdjustment)
            .add(this.platformAbsorbedAmount);
    if (allocated.compareTo(amount) != 0) {
      throw new IllegalArgumentException("Refund allocation must exactly equal the refund amount");
    }
    this.reference = externalReference.trim();
    this.completedByUserId = actorId;
    this.completedAt = Instant.now();
    this.status = RefundStatus.COMPLETED;
  }

  public void cancel(String cancellationReason, UUID actorId) {
    if (status != RefundStatus.INITIATED) {
      throw new IllegalStateException("Only an initiated refund can be cancelled");
    }
    if (actorId == null || cancellationReason == null || cancellationReason.isBlank()) {
      throw new IllegalArgumentException("Refund cancellation actor and reason are required");
    }
    this.cancelledByUserId = actorId;
    this.cancelledAt = Instant.now();
    this.cancellationReason = cancellationReason.trim();
    this.status = RefundStatus.CANCELLED;
  }

  void setLegacyRecoveryPlan(
      BigDecimal inspectorReceivableAmount, BigDecimal platformAbsorbedPlanAmount) {
    if (status != RefundStatus.COMPLETED || !isAllocationMissing()) {
      throw new IllegalStateException(
          "Recovery plan applies only to an unreconciled completed legacy refund");
    }
    this.inspectorReceivableAmount =
        requireNonNegative(inspectorReceivableAmount, "Inspector receivable");
    this.platformAbsorbedPlanAmount =
        requireNonNegative(platformAbsorbedPlanAmount, "Platform absorbed plan");
    if (this.inspectorReceivableAmount.add(this.platformAbsorbedPlanAmount).compareTo(amount) > 0) {
      throw new IllegalArgumentException(
          "Legacy receivable plan and platform absorption cannot exceed the refund amount");
    }
  }

  private BigDecimal requireNonNegative(BigDecimal value, String label) {
    if (value == null || value.signum() < 0)
      throw new IllegalArgumentException(label + " must be non-negative");
    return value.setScale(2, java.math.RoundingMode.HALF_UP);
  }

  void reconcileAllocation(
      BigDecimal inspectorPayoutReduction,
      BigDecimal platformCommissionAdjustment,
      BigDecimal vatAdjustment,
      BigDecimal tncnAdjustment,
      BigDecimal platformAbsorbedAmount,
      UUID managerId,
      String reference,
      String note) {
    if (this.inspectorPayoutReduction != null
        || inspectorPayoutReduction == null
        || platformCommissionAdjustment == null
        || vatAdjustment == null
        || tncnAdjustment == null
        || platformAbsorbedAmount == null
        || managerId == null
        || reference == null
        || reference.isBlank()
        || note == null
        || note.isBlank()) {
      throw new IllegalStateException("Refund allocation is already reconciled or incomplete");
    }
    BigDecimal normalizedInspectorReduction =
        requireNonNegative(inspectorPayoutReduction, "Inspector payout reduction");
    BigDecimal normalizedCommission =
        requireNonNegative(platformCommissionAdjustment, "Platform commission adjustment");
    BigDecimal normalizedVat = requireNonNegative(vatAdjustment, "VAT adjustment");
    BigDecimal normalizedTncn = requireNonNegative(tncnAdjustment, "TNCN adjustment");
    BigDecimal normalizedPlatform =
        requireNonNegative(platformAbsorbedAmount, "Platform absorbed amount");
    BigDecimal total =
        normalizedInspectorReduction
            .add(normalizedCommission)
            .add(normalizedVat)
            .add(normalizedTncn)
            .add(normalizedPlatform);
    if (total.compareTo(amount) != 0) {
      throw new IllegalArgumentException("Refund allocation must exactly equal the refund amount");
    }
    this.inspectorPayoutReduction = normalizedInspectorReduction;
    this.platformCommissionAdjustment = normalizedCommission;
    this.vatAdjustment = normalizedVat;
    this.tncnAdjustment = normalizedTncn;
    this.platformAbsorbedAmount = normalizedPlatform;
    this.allocationReconciledByUserId = managerId;
    this.allocationReconciliationReference = reference.trim();
    this.allocationReconciliationNote = note.trim();
    this.allocationReconciledAt = Instant.now();
  }

  boolean isAllocationMissing() {
    return inspectorPayoutReduction == null;
  }

  void attributeTo(UUID managerId) {
    if (managerId == null) {
      throw new IllegalArgumentException("Refund recorder is required");
    }
    recordedByUserId = managerId;
  }

  public UUID getId() {
    return id;
  }

  public BigDecimal getInspectorReceivableAmount() {
    return inspectorReceivableAmount;
  }

  public BigDecimal getPlatformAbsorbedPlanAmount() {
    return platformAbsorbedPlanAmount;
  }

  public RefundStatus getStatus() {
    return status;
  }

  public String getRequestReference() {
    return requestReference;
  }

  public UUID getInitiatedByUserId() {
    return initiatedByUserId;
  }

  public Instant getInitiatedAt() {
    return initiatedAt;
  }

  public UUID getCompletedByUserId() {
    return completedByUserId;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }

  public UUID getCancelledByUserId() {
    return cancelledByUserId;
  }

  public Instant getCancelledAt() {
    return cancelledAt;
  }

  public String getCancellationReason() {
    return cancellationReason;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public UUID getAllocationReconciledByUserId() {
    return allocationReconciledByUserId;
  }

  public String getAllocationReconciliationReference() {
    return allocationReconciliationReference;
  }

  public String getAllocationReconciliationNote() {
    return allocationReconciliationNote;
  }

  public Instant getAllocationReconciledAt() {
    return allocationReconciledAt;
  }

  public BigDecimal getInspectorPayoutReduction() {
    return inspectorPayoutReduction;
  }

  public BigDecimal getPlatformCommissionAdjustment() {
    return platformCommissionAdjustment;
  }

  public BigDecimal getVatAdjustment() {
    return vatAdjustment;
  }

  public BigDecimal getTncnAdjustment() {
    return tncnAdjustment;
  }

  public BigDecimal getPlatformAbsorbedAmount() {
    return platformAbsorbedAmount;
  }

  public String getReference() {
    return reference;
  }

  public String getReason() {
    return reason;
  }

  public UUID getRecordedByUserId() {
    return recordedByUserId;
  }

  public Instant getRecordedAt() {
    return recordedAt;
  }
}
