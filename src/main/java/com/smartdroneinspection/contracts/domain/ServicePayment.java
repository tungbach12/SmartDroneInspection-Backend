package com.smartdroneinspection.contracts.domain;

import com.smartdroneinspection.contracts.domain.ServiceRequestOccurrence.OccurrencePaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "service_payments")
public class ServicePayment {

  @Id
  @Column(name = "occurrence_id")
  private UUID occurrenceId;

  @MapsId
  @OneToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "occurrence_id")
  private ServiceRequestOccurrence occurrence;

  @Enumerated(EnumType.STRING)
  @Column(name = "payment_status", nullable = false, length = 24)
  private OccurrencePaymentStatus paymentStatus = OccurrencePaymentStatus.PENDING;

  @Column(name = "refunded_amount", nullable = false, precision = 15, scale = 2)
  private BigDecimal refundedAmount = BigDecimal.ZERO;

  @Column(name = "refund_status", nullable = false, length = 24)
  private String refundStatus = "NONE";

  @OneToMany(
      mappedBy = "payment",
      cascade = jakarta.persistence.CascadeType.ALL,
      orphanRemoval = true)
  private List<ServiceRefund> refunds = new ArrayList<>();

  @Column(name = "payment_reference", length = 255)
  private String paymentReference;

  @Column(name = "payment_confirmed_at")
  private Instant paymentConfirmedAt;

  @Column(name = "payos_order_code", unique = true)
  private Long payosOrderCode;

  @Column(name = "payment_method", length = 30)
  private String paymentMethod;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  @Version
  @Column(name = "row_version", nullable = false)
  private long rowVersion;

  protected ServicePayment() {}

  ServicePayment(ServiceRequestOccurrence occurrence) {
    this.occurrence = occurrence;
  }

  void paymentDue() {
    if (paymentStatus == OccurrencePaymentStatus.PENDING) {
      paymentStatus = OccurrencePaymentStatus.PAYMENT_DUE;
      updatedAt = Instant.now();
    }
  }

  void assignPayOSOrder(long orderCode) {
    assignGatewayOrder(orderCode, "PAYOS");
  }

  void assignVnPayOrder(long orderCode) {
    assignGatewayOrder(orderCode, "VNPAY");
  }

  boolean hasActiveGatewayOrder(String method) {
    return paymentStatus == OccurrencePaymentStatus.PAYMENT_DUE
        && payosOrderCode != null
        && payosOrderCode > 0
        && method.equals(paymentMethod);
  }

  private void assignGatewayOrder(long orderCode, String method) {
    if (paymentStatus != OccurrencePaymentStatus.PAYMENT_DUE) {
      throw new IllegalStateException("Payment is not due");
    }
    if (payosOrderCode != null && payosOrderCode > 0) {
      if (payosOrderCode == orderCode && method.equals(paymentMethod)) {
        return;
      }
      throw new IllegalStateException(
          "An active payment order already exists; resolve or cancel it before changing gateway");
    }
    payosOrderCode = orderCode;
    paymentMethod = method;
    updatedAt = Instant.now();
  }

  void clearUnpaidGatewayOrder(long orderCode, String method) {
    if (paymentStatus != OccurrencePaymentStatus.PAYMENT_DUE
        || payosOrderCode == null
        || payosOrderCode.longValue() != orderCode
        || !method.equalsIgnoreCase(paymentMethod)) {
      throw new IllegalStateException("Only the matching unpaid gateway order may be cleared");
    }
    payosOrderCode = null;
    paymentMethod = null;
    updatedAt = Instant.now();
  }

  ServiceRefund recordRefund(
      BigDecimal totalPrice, BigDecimal amount, String reference, String reason, UUID managerId) {
    if (paymentStatus != OccurrencePaymentStatus.PAID) {
      throw new IllegalStateException("Only a paid occurrence may be refunded");
    }
    if (reference == null || reference.isBlank() || reason == null || reason.isBlank()) {
      throw new IllegalArgumentException("Refund reference and reason are required");
    }
    if (amount == null || amount.signum() <= 0 || totalPrice == null) {
      throw new IllegalArgumentException("Refund amount must be positive");
    }
    BigDecimal nextTotal = refundedAmount.add(amount).setScale(2, java.math.RoundingMode.HALF_UP);
    if (nextTotal.compareTo(totalPrice) > 0) {
      throw new IllegalArgumentException("Cumulative refunds cannot exceed the captured payment");
    }
    ServiceRefund refund = new ServiceRefund(this, amount, reference.trim(), reason.trim());
    refund.attributeTo(managerId);
    refunds.add(refund);
    refundedAmount = nextTotal;
    refundStatus = nextTotal.compareTo(totalPrice) == 0 ? "FULL" : "PARTIAL";
    updatedAt = Instant.now();
    return refund;
  }

  void confirm(String reference, String method) {
    if (paymentStatus == OccurrencePaymentStatus.PAID) {
      String normalizedMethod = (method != null && !method.isBlank()) ? method : "PAYOS";
      if (reference.trim().equals(paymentReference) && normalizedMethod.equals(paymentMethod)) {
        return;
      }
      throw new IllegalStateException("Payment was already confirmed with different details");
    }
    paymentStatus = OccurrencePaymentStatus.PAID;
    paymentReference = reference.trim();
    paymentMethod = (method != null && !method.isBlank()) ? method : "PAYOS";
    paymentConfirmedAt = Instant.now();
    updatedAt = paymentConfirmedAt;
  }

  public UUID getOccurrenceId() {
    return occurrenceId;
  }

  public OccurrencePaymentStatus getPaymentStatus() {
    return paymentStatus;
  }

  public String getPaymentReference() {
    return paymentReference;
  }

  public Instant getPaymentConfirmedAt() {
    return paymentConfirmedAt;
  }

  public Long getPayosOrderCode() {
    return payosOrderCode;
  }

  public String getPaymentMethod() {
    return paymentMethod;
  }

  public BigDecimal getRefundedAmount() {
    return refundedAmount;
  }

  public String getRefundStatus() {
    return refundStatus;
  }

  public List<ServiceRefund> getRefunds() {
    return List.copyOf(refunds);
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
