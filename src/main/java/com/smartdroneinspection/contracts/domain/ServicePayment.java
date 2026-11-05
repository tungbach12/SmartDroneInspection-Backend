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
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
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
    payosOrderCode = orderCode;
    paymentMethod = "PAYOS";
    updatedAt = Instant.now();
  }

  void assignVnPayOrder(long orderCode) {
    payosOrderCode = orderCode;
    paymentMethod = "VNPAY";
    updatedAt = Instant.now();
  }

  void confirm(String reference, String method) {
    if (paymentStatus == OccurrencePaymentStatus.PAID) {
      if (reference.trim().equals(paymentReference)) {
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

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
