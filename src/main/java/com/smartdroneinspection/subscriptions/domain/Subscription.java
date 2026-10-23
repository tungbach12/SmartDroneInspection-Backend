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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "subscriptions")
public class Subscription {

  @Id @GeneratedValue private UUID id;

  @Column(name = "organization_id", nullable = false, updatable = false)
  private UUID organizationId;

  @Column(name = "plan_code", nullable = false, length = 32)
  private String planCode;

  @Column(name = "billing_period_months", nullable = false)
  private int billingPeriodMonths;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private Status status;

  @Column(name = "starts_at")
  private Instant startsAt;

  @Column(name = "ends_at")
  private Instant endsAt;

  @Column(name = "external_reference", length = 128)
  private String externalReference;

  @Column(name = "agreed_amount_usd", precision = 12, scale = 2)
  private BigDecimal agreedAmountUsd;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "terms_snapshot", columnDefinition = "jsonb")
  private String termsSnapshot;

  @Column(name = "confirmed_by_user_id")
  private UUID confirmedByUserId;

  @Column(name = "confirmed_at")
  private Instant confirmedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "row_version", nullable = false)
  private long rowVersion;

  protected Subscription() {}

  public static Subscription requestEnterprise(
      UUID organizationId,
      int billingPeriodMonths,
      BigDecimal proposedAmountUsd,
      String termsSnapshot) {
    Objects.requireNonNull(organizationId, "Organization ID is required");
    if (billingPeriodMonths < 1 || billingPeriodMonths > 120) {
      throw new IllegalArgumentException(
          "Billing period must be between 1 and 120 months, got: " + billingPeriodMonths);
    }
    Subscription s = new Subscription();
    s.organizationId = organizationId;
    s.planCode = ContractTerms.PLAN_CODE;
    s.billingPeriodMonths = billingPeriodMonths;
    // A customer-submitted price proposal is not a platform-approved contract amount. The proposal
    // is retained in termsSnapshot; agreedAmountUsd is set only when ADMIN confirms settlement.
    s.agreedAmountUsd = null;
    s.status = Status.PENDING;
    s.termsSnapshot = termsSnapshot;
    Instant now = Instant.now();
    s.createdAt = now;
    s.updatedAt = now;
    return s;
  }

  public static Subscription requestEnterprise(
      UUID organizationId, int billingPeriodMonths, String termsSnapshot) {
    return requestEnterprise(organizationId, billingPeriodMonths, null, termsSnapshot);
  }

  public void confirmPayment(
      String externalReference,
      Instant startsAt,
      Instant endsAt,
      BigDecimal finalAgreedAmount,
      UUID confirmedByUserId) {
    if (externalReference == null || externalReference.isBlank()) {
      throw new IllegalArgumentException("External reference is required for payment confirmation");
    }
    if (startsAt == null || endsAt == null) {
      throw new IllegalArgumentException("Starts at and ends at timestamps are required");
    }
    if (!endsAt.isAfter(startsAt)) {
      throw new IllegalArgumentException("Ends at must be strictly after starts at");
    }
    this.externalReference = externalReference.trim();
    this.startsAt = startsAt;
    this.endsAt = endsAt;
    if (finalAgreedAmount != null) {
      this.agreedAmountUsd = finalAgreedAmount;
    }
    this.confirmedByUserId = confirmedByUserId;
    this.confirmedAt = Instant.now();
    this.status = Status.ACTIVE;
    this.updatedAt = Instant.now();
  }

  public void confirmPayment(
      String externalReference, Instant startsAt, Instant endsAt, UUID confirmedByUserId) {
    confirmPayment(externalReference, startsAt, endsAt, null, confirmedByUserId);
  }

  public static Subscription activate(
      UUID organizationId,
      Integer billingPeriodMonths,
      Instant startsAt,
      Instant endsAt,
      String externalReference,
      String termsSnapshot,
      UUID confirmedByUserId) {
    Objects.requireNonNull(organizationId, "organizationId is required");
    Objects.requireNonNull(confirmedByUserId, "confirmedByUserId is required");
    if (billingPeriodMonths == null
        || (billingPeriodMonths != 1 && billingPeriodMonths != 6 && billingPeriodMonths != 12)) {
      throw new IllegalArgumentException("billingPeriodMonths must be 1, 6, or 12");
    }
    if (startsAt != null && endsAt != null && !endsAt.isAfter(startsAt)) {
      throw new IllegalArgumentException("endsAt must be after startsAt");
    }
    if (externalReference == null || externalReference.isBlank()) {
      throw new IllegalArgumentException("externalReference is required");
    }
    Subscription subscription = new Subscription();
    subscription.organizationId = organizationId;
    subscription.planCode = ContractTerms.PLAN_CODE;
    subscription.billingPeriodMonths = billingPeriodMonths;
    subscription.status = Status.ACTIVE;
    subscription.startsAt = startsAt;
    subscription.endsAt = endsAt;
    subscription.externalReference = externalReference.trim();
    subscription.termsSnapshot = termsSnapshot;
    subscription.confirmedByUserId = confirmedByUserId;
    subscription.confirmedAt = Instant.now();
    subscription.createdAt = subscription.confirmedAt;
    subscription.updatedAt = subscription.confirmedAt;
    return subscription;
  }

  public boolean hasEnded(Instant at) {
    return status == Status.ACTIVE && endsAt != null && !endsAt.isAfter(at);
  }

  public void expire(Instant effectiveAt) {
    this.status = Status.EXPIRED;
    if (effectiveAt != null && (this.endsAt == null || this.endsAt.isAfter(effectiveAt))) {
      this.endsAt = effectiveAt;
    }
    this.updatedAt = Instant.now();
  }

  public void cancel(String reason) {
    this.status = Status.CANCELLED;
    this.updatedAt = Instant.now();
  }

  public boolean covers(Instant at) {
    return status == Status.ACTIVE
        && (startsAt == null || !startsAt.isAfter(at))
        && (endsAt == null || at.isBefore(endsAt));
  }

  public UUID getId() {
    return id;
  }

  public UUID getOrganizationId() {
    return organizationId;
  }

  public String getPlanCode() {
    return planCode;
  }

  public int getBillingPeriodMonths() {
    return billingPeriodMonths;
  }

  public Status getStatus() {
    return status;
  }

  public Instant getStartsAt() {
    return startsAt;
  }

  public Instant getEndsAt() {
    return endsAt;
  }

  public String getExternalReference() {
    return externalReference;
  }

  public BigDecimal getAgreedAmountUsd() {
    return agreedAmountUsd;
  }

  public String getTermsSnapshot() {
    return termsSnapshot;
  }

  public UUID getConfirmedByUserId() {
    return confirmedByUserId;
  }

  public Instant getConfirmedAt() {
    return confirmedAt;
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
    PENDING,
    ACTIVE,
    PAST_DUE,
    SUSPENDED,
    EXPIRED,
    CANCELLED
  }
}
