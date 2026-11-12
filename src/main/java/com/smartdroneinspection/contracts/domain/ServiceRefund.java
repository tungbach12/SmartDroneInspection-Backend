package com.smartdroneinspection.contracts.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

  @Id @GeneratedValue private UUID id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "occurrence_id", nullable = false)
  private ServicePayment payment;

  @Column(name = "amount", nullable = false, precision = 15, scale = 2)
  private BigDecimal amount;

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

  @Column(name = "refund_reference", nullable = false, length = 255)
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
      BigDecimal inspectorPayoutReduction,
      BigDecimal platformCommissionAdjustment,
      BigDecimal vatAdjustment,
      BigDecimal tncnAdjustment,
      BigDecimal platformAbsorbedAmount,
      String reference,
      String reason) {
    this.payment = payment;
    this.amount = amount.setScale(2, java.math.RoundingMode.HALF_UP);
    this.inspectorPayoutReduction = inspectorPayoutReduction;
    this.platformCommissionAdjustment = platformCommissionAdjustment;
    this.vatAdjustment = vatAdjustment;
    this.tncnAdjustment = tncnAdjustment;
    this.platformAbsorbedAmount = platformAbsorbedAmount;
    this.reference = reference;
    this.reason = reason;
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
    BigDecimal total =
        inspectorPayoutReduction
            .add(platformCommissionAdjustment)
            .add(vatAdjustment)
            .add(tncnAdjustment)
            .add(platformAbsorbedAmount);
    if (total.compareTo(amount) != 0) {
      throw new IllegalArgumentException("Refund allocation must exactly equal the refund amount");
    }
    this.inspectorPayoutReduction = inspectorPayoutReduction;
    this.platformCommissionAdjustment = platformCommissionAdjustment;
    this.vatAdjustment = vatAdjustment;
    this.tncnAdjustment = tncnAdjustment;
    this.platformAbsorbedAmount = platformAbsorbedAmount;
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
