package com.smartdroneinspection.subscriptions.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Immutable
@Table(name = "subscriptions")
public class Subscription {

  @Id @GeneratedValue private UUID id;

  @Column(name = "organization_id", nullable = false, updatable = false)
  private UUID organizationId;

  @Column(name = "plan_code", nullable = false, length = 32, updatable = false)
  private String planCode;

  @Column(name = "billing_period_months", nullable = false, updatable = false)
  private short billingPeriodMonths;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24, updatable = false)
  private Status status;

  @Column(name = "starts_at", updatable = false)
  private Instant startsAt;

  @Column(name = "ends_at", updatable = false)
  private Instant endsAt;

  @Column(name = "external_reference", length = 128, updatable = false)
  private String externalReference;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "terms_snapshot", columnDefinition = "jsonb", updatable = false)
  private String termsSnapshot;

  @Column(name = "confirmed_by_user_id", updatable = false)
  private UUID confirmedByUserId;

  @Column(name = "confirmed_at", updatable = false)
  private Instant confirmedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "row_version", nullable = false)
  private long rowVersion;

  protected Subscription() {}

  public static Subscription activate(
      UUID organizationId,
      int billingPeriodMonths,
      Instant startsAt,
      Instant endsAt,
      String externalReference,
      String termsSnapshot,
      UUID confirmedByUserId) {
    Objects.requireNonNull(organizationId, "organizationId is required");
    Objects.requireNonNull(confirmedByUserId, "confirmedByUserId is required");
    if (billingPeriodMonths != 1 && billingPeriodMonths != 6 && billingPeriodMonths != 12) {
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
    subscription.planCode = "ENTERPRISE";
    subscription.billingPeriodMonths = (short) billingPeriodMonths;
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

  public String getTermsSnapshot() {
    return termsSnapshot;
  }

  public UUID getConfirmedByUserId() {
    return confirmedByUserId;
  }

  public Instant getConfirmedAt() {
    return confirmedAt;
  }

  public boolean covers(Instant at) {
    return status == Status.ACTIVE
        && (startsAt == null || !startsAt.isAfter(at))
        && (endsAt == null || at.isBefore(endsAt));
  }

  public boolean hasEnded(Instant at) {
    return endsAt != null && !endsAt.isAfter(at);
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
