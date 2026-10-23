package com.smartdroneinspection.subscriptions.domain;

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
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "payment_transactions")
public class PaymentTransaction {

  @Id @GeneratedValue private UUID id;

  @Column(name = "subscription_id", nullable = false, updatable = false)
  private UUID subscriptionId;

  @Column(name = "paypal_order_id", nullable = false, length = 64, updatable = false)
  private String paypalOrderId;

  @Column(name = "paypal_capture_id", length = 64, unique = true)
  private String paypalCaptureId;

  @Column(nullable = false, precision = 12, scale = 2)
  private BigDecimal amount;

  @Column(nullable = false, length = 3)
  private String currency;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private Status status;

  @Column(name = "payer_email", length = 255)
  private String payerEmail;

  @Column(name = "captured_at")
  private Instant capturedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "row_version", nullable = false)
  private long rowVersion;

  protected PaymentTransaction() {}

  /**
   * Records an order the platform created but has not settled yet.
   *
   * <p>The row is written before the payer is sent to the provider, so a later capture or webhook
   * can be tied back to the subscription the order belongs to.
   */
  public static PaymentTransaction recordPendingOrder(
      UUID subscriptionId, String paypalOrderId, BigDecimal amount, String currency) {
    Objects.requireNonNull(subscriptionId, "Subscription ID is required");
    Objects.requireNonNull(paypalOrderId, "PayPal order ID is required");
    Objects.requireNonNull(amount, "Payment amount is required");

    PaymentTransaction pt = new PaymentTransaction();
    pt.subscriptionId = subscriptionId;
    pt.paypalOrderId = paypalOrderId.trim();
    pt.paypalCaptureId = null;
    pt.amount = amount;
    pt.currency = (currency != null && !currency.isBlank()) ? currency.trim().toUpperCase() : "USD";
    pt.status = Status.PENDING;
    Instant now = Instant.now();
    pt.capturedAt = null;
    pt.createdAt = now;
    pt.updatedAt = now;
    return pt;
  }

  /**
   * Completes a pending order with the capture the provider confirmed.
   *
   * <p>The capture id, amount, currency, and payer all come from the provider. A transaction that
   * was already completed is not completed twice, so a repeated notification cannot settle an order
   * again.
   */
  public void markCompleted(
      String paypalCaptureId, BigDecimal settledAmount, String settledCurrency, String payerEmail) {
    if (status == Status.COMPLETED) {
      throw new IllegalStateException("This payment was already completed");
    }
    Objects.requireNonNull(paypalCaptureId, "Payment capture ID is required");
    Objects.requireNonNull(settledAmount, "Settled amount is required");
    this.paypalCaptureId = paypalCaptureId.trim();
    this.amount = settledAmount;
    this.currency =
        (settledCurrency != null && !settledCurrency.isBlank())
            ? settledCurrency.trim().toUpperCase()
            : this.currency;
    this.payerEmail = payerEmail != null ? payerEmail.trim().toLowerCase() : null;
    this.status = Status.COMPLETED;
    this.capturedAt = Instant.now();
    this.updatedAt = Instant.now();
  }

  public static PaymentTransaction recordCompletedCapture(
      UUID subscriptionId,
      String paypalOrderId,
      String paypalCaptureId,
      BigDecimal amount,
      String currency,
      String payerEmail,
      Instant capturedAt) {
    Objects.requireNonNull(subscriptionId, "Subscription ID is required");
    Objects.requireNonNull(paypalOrderId, "PayPal order ID is required");
    Objects.requireNonNull(paypalCaptureId, "PayPal capture ID is required");
    Objects.requireNonNull(amount, "Payment amount is required");

    PaymentTransaction pt = new PaymentTransaction();
    pt.subscriptionId = subscriptionId;
    pt.paypalOrderId = paypalOrderId.trim();
    pt.paypalCaptureId = paypalCaptureId.trim();
    pt.amount = amount;
    pt.currency = (currency != null && !currency.isBlank()) ? currency.trim().toUpperCase() : "USD";
    pt.status = Status.COMPLETED;
    pt.payerEmail = payerEmail != null ? payerEmail.trim().toLowerCase() : null;
    Instant now = Instant.now();
    pt.capturedAt = capturedAt != null ? capturedAt : now;
    pt.createdAt = now;
    pt.updatedAt = now;
    return pt;
  }

  public UUID getId() {
    return id;
  }

  public UUID getSubscriptionId() {
    return subscriptionId;
  }

  public String getPaypalOrderId() {
    return paypalOrderId;
  }

  public String getPaypalCaptureId() {
    return paypalCaptureId;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public String getCurrency() {
    return currency;
  }

  public Status getStatus() {
    return status;
  }

  public String getPayerEmail() {
    return payerEmail;
  }

  public Instant getCapturedAt() {
    return capturedAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public long getRowVersion() {
    return rowVersion;
  }

  public enum Status {
    COMPLETED,
    PENDING,
    DECLINED,
    REFUNDED
  }
}
