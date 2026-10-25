package com.smartdroneinspection.assets.domain;

import com.smartdroneinspection.assets.domain.enums.DroneLeaseStatus;
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
@Table(name = "drone_leases")
public class DroneLease {

  @Id @GeneratedValue private UUID id;

  @Column(name = "platform_drone_id", nullable = false)
  private UUID platformDroneId;

  @Column(name = "organization_id", nullable = false)
  private UUID organizationId;

  @Column(name = "customer_drone_id")
  private UUID customerDroneId;

  @Column(name = "requested_by_user_id", nullable = false)
  private UUID requestedByUserId;

  @Column(name = "lease_period_months", nullable = false)
  private int leasePeriodMonths;

  @Column(name = "monthly_rate_usd", nullable = false, precision = 12, scale = 2)
  private BigDecimal monthlyRateUsd;

  @Column(name = "agreed_amount_usd", nullable = false, precision = 12, scale = 2)
  private BigDecimal agreedAmountUsd;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private DroneLeaseStatus status;

  @Column(name = "starts_at")
  private Instant startsAt;

  @Column(name = "ends_at")
  private Instant endsAt;

  @Column(name = "reclaimed_at")
  private Instant reclaimedAt;

  @Column(name = "external_reference", length = 128)
  private String externalReference;

  @Column(name = "paypal_order_id", length = 64)
  private String paypalOrderId;

  @Column(name = "paypal_capture_id", length = 64)
  private String paypalCaptureId;

  @Column(name = "confirmed_by_user_id")
  private UUID confirmedByUserId;

  @Column(name = "confirmed_at")
  private Instant confirmedAt;

  @Column(length = 2000)
  private String notes;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "row_version", nullable = false)
  private long rowVersion;

  protected DroneLease() {}

  public DroneLease(
      UUID platformDroneId,
      UUID organizationId,
      UUID requestedByUserId,
      int leasePeriodMonths,
      BigDecimal monthlyRateUsd,
      BigDecimal agreedAmountUsd,
      String notes) {
    this.platformDroneId = Objects.requireNonNull(platformDroneId, "Platform drone id is required");
    this.organizationId = Objects.requireNonNull(organizationId, "Organization id is required");
    this.requestedByUserId =
        Objects.requireNonNull(requestedByUserId, "Requested-by user id is required");
    if (leasePeriodMonths < 1 || leasePeriodMonths > 60) {
      throw new IllegalArgumentException("Lease period must be between 1 and 60 months");
    }
    this.leasePeriodMonths = leasePeriodMonths;
    this.monthlyRateUsd = Objects.requireNonNull(monthlyRateUsd, "Monthly rate is required");
    this.agreedAmountUsd = Objects.requireNonNull(agreedAmountUsd, "Agreed amount is required");
    this.status = DroneLeaseStatus.PENDING;
    this.notes = notes;
    this.createdAt = Instant.now();
    this.updatedAt = this.createdAt;
  }

  public UUID getId() {
    return id;
  }

  public UUID getPlatformDroneId() {
    return platformDroneId;
  }

  public UUID getOrganizationId() {
    return organizationId;
  }

  public UUID getCustomerDroneId() {
    return customerDroneId;
  }

  public UUID getRequestedByUserId() {
    return requestedByUserId;
  }

  public int getLeasePeriodMonths() {
    return leasePeriodMonths;
  }

  public BigDecimal getMonthlyRateUsd() {
    return monthlyRateUsd;
  }

  public BigDecimal getAgreedAmountUsd() {
    return agreedAmountUsd;
  }

  public DroneLeaseStatus getStatus() {
    return status;
  }

  public Instant getStartsAt() {
    return startsAt;
  }

  public Instant getEndsAt() {
    return endsAt;
  }

  public Instant getReclaimedAt() {
    return reclaimedAt;
  }

  public String getExternalReference() {
    return externalReference;
  }

  public String getPaypalOrderId() {
    return paypalOrderId;
  }

  public String getPaypalCaptureId() {
    return paypalCaptureId;
  }

  public UUID getConfirmedByUserId() {
    return confirmedByUserId;
  }

  public Instant getConfirmedAt() {
    return confirmedAt;
  }

  public String getNotes() {
    return notes;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void recordPayPalOrder(String orderId) {
    this.paypalOrderId = Objects.requireNonNull(orderId, "PayPal order id is required");
    this.updatedAt = Instant.now();
  }

  public void activateWire(
      String externalReference,
      Instant startsAt,
      Instant endsAt,
      BigDecimal finalAgreedAmount,
      UUID confirmedByUserId,
      UUID customerDroneId,
      String notes) {
    this.externalReference =
        Objects.requireNonNull(externalReference, "External reference is required").trim();
    this.startsAt = Objects.requireNonNull(startsAt, "Start timestamp is required");
    this.endsAt = Objects.requireNonNull(endsAt, "End timestamp is required");
    this.agreedAmountUsd = Objects.requireNonNull(finalAgreedAmount, "Agreed amount is required");
    this.confirmedByUserId = confirmedByUserId;
    this.confirmedAt = Instant.now();
    this.customerDroneId = Objects.requireNonNull(customerDroneId, "Customer drone id is required");
    this.status = DroneLeaseStatus.ACTIVE;
    if (notes != null && !notes.isBlank()) {
      this.notes = notes.trim();
    }
    this.updatedAt = this.confirmedAt;
  }

  public void activatePayPal(
      String orderId,
      String captureId,
      Instant startsAt,
      Instant endsAt,
      BigDecimal settledAmount,
      UUID confirmedByUserId,
      UUID customerDroneId) {
    this.paypalOrderId = Objects.requireNonNull(orderId, "PayPal order id is required");
    this.paypalCaptureId = Objects.requireNonNull(captureId, "PayPal capture id is required");
    this.externalReference = "PAYPAL:" + captureId;
    this.startsAt = Objects.requireNonNull(startsAt, "Start timestamp is required");
    this.endsAt = Objects.requireNonNull(endsAt, "End timestamp is required");
    this.agreedAmountUsd = Objects.requireNonNull(settledAmount, "Settled amount is required");
    this.confirmedByUserId = confirmedByUserId;
    this.confirmedAt = Instant.now();
    this.customerDroneId = Objects.requireNonNull(customerDroneId, "Customer drone id is required");
    this.status = DroneLeaseStatus.ACTIVE;
    this.updatedAt = this.confirmedAt;
  }

  public void reclaim(Instant reclaimedAt, String notes) {
    this.status = DroneLeaseStatus.RECLAIMED;
    this.reclaimedAt = Objects.requireNonNull(reclaimedAt, "Reclaimed timestamp is required");
    if (notes != null && !notes.isBlank()) {
      this.notes = notes.trim();
    }
    this.updatedAt = this.reclaimedAt;
  }

  public void cancel(String notes) {
    this.status = DroneLeaseStatus.CANCELLED;
    if (notes != null && !notes.isBlank()) {
      this.notes = notes.trim();
    }
    this.updatedAt = Instant.now();
  }
}
