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

  @Column(name = "refund_reference", nullable = false, length = 255)
  private String reference;

  @Column(name = "reason", nullable = false, length = 2000)
  private String reason;

  @Column(name = "recorded_by_user_id", nullable = false)
  private UUID recordedByUserId;

  @Column(name = "recorded_at", nullable = false, updatable = false)
  private Instant recordedAt = Instant.now();

  protected ServiceRefund() {}

  ServiceRefund(ServicePayment payment, BigDecimal amount, String reference, String reason) {
    this.payment = payment;
    this.amount = amount.setScale(2, java.math.RoundingMode.HALF_UP);
    this.reference = reference;
    this.reason = reason;
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
