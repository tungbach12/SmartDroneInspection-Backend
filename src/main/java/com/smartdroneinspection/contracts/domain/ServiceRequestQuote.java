package com.smartdroneinspection.contracts.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "service_request_quotes")
public class ServiceRequestQuote {

  @Id @GeneratedValue private UUID id;

  @Column(name = "request_id", nullable = false)
  private UUID requestId;

  @Column(name = "version_no", nullable = false)
  private int versionNo;

  @Column(name = "scope_snapshot", nullable = false, length = 4000)
  private String scopeSnapshot;

  @Column(name = "first_occurrence_start_at", nullable = false)
  private Instant firstOccurrenceStartAt;

  @Column(name = "first_occurrence_end_at", nullable = false)
  private Instant firstOccurrenceEndAt;

  @Column(name = "inspector_id", nullable = false)
  private UUID inspectorId;

  @Column(name = "drone_id", nullable = false)
  private UUID droneId;

  @Column(name = "currency", nullable = false, length = 3)
  private String currency = "VND";

  @Column(name = "price_per_occurrence_vnd", precision = 15, scale = 2)
  private BigDecimal pricePerOccurrenceVnd;

  @Column(name = "price_per_occurrence_usd", precision = 12, scale = 2)
  private BigDecimal pricePerOccurrenceUsd;

  @Column(name = "issued_by_user_id", nullable = false)
  private UUID issuedByUserId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt = Instant.now();

  protected ServiceRequestQuote() {}

  public ServiceRequestQuote(
      UUID requestId,
      int versionNo,
      String scopeSnapshot,
      Instant firstOccurrenceStartAt,
      Instant firstOccurrenceEndAt,
      UUID inspectorId,
      UUID droneId,
      BigDecimal pricePerOccurrenceUsd,
      UUID issuedByUserId) {
    this(
        requestId,
        versionNo,
        scopeSnapshot,
        firstOccurrenceStartAt,
        firstOccurrenceEndAt,
        inspectorId,
        droneId,
        "USD",
        null,
        pricePerOccurrenceUsd,
        issuedByUserId);
  }

  public ServiceRequestQuote(
      UUID requestId,
      int versionNo,
      String scopeSnapshot,
      Instant firstOccurrenceStartAt,
      Instant firstOccurrenceEndAt,
      UUID inspectorId,
      UUID droneId,
      String currency,
      BigDecimal pricePerOccurrenceVnd,
      BigDecimal pricePerOccurrenceUsd,
      UUID issuedByUserId) {
    if (requestId == null
        || versionNo < 1
        || scopeSnapshot == null
        || scopeSnapshot.isBlank()
        || firstOccurrenceStartAt == null
        || firstOccurrenceEndAt == null
        || !firstOccurrenceEndAt.isAfter(firstOccurrenceStartAt)
        || inspectorId == null
        || droneId == null
        || issuedByUserId == null) {
      throw new IllegalArgumentException("Quote fields are invalid");
    }
    String resolvedCurrency =
        (currency != null && !currency.isBlank()) ? currency.trim().toUpperCase() : "VND";
    if (!"USD".equals(resolvedCurrency) && !"VND".equals(resolvedCurrency)) {
      throw new IllegalArgumentException("Unsupported currency: " + currency);
    }
    if ("VND".equals(resolvedCurrency)) {
      if (pricePerOccurrenceVnd == null || pricePerOccurrenceVnd.signum() <= 0) {
        throw new IllegalArgumentException("VND quote price must be positive");
      }
      this.pricePerOccurrenceVnd =
          pricePerOccurrenceVnd.setScale(2, java.math.RoundingMode.HALF_UP);
      this.pricePerOccurrenceUsd = null;
    } else {
      if (pricePerOccurrenceUsd == null || pricePerOccurrenceUsd.signum() <= 0) {
        throw new IllegalArgumentException("USD quote price must be positive");
      }
      this.pricePerOccurrenceUsd =
          pricePerOccurrenceUsd.setScale(2, java.math.RoundingMode.HALF_UP);
      this.pricePerOccurrenceVnd = null;
    }
    this.requestId = requestId;
    this.versionNo = versionNo;
    this.scopeSnapshot = scopeSnapshot.trim();
    this.firstOccurrenceStartAt = firstOccurrenceStartAt;
    this.firstOccurrenceEndAt = firstOccurrenceEndAt;
    this.inspectorId = inspectorId;
    this.droneId = droneId;
    this.currency = resolvedCurrency;
    this.issuedByUserId = issuedByUserId;
  }

  public BigDecimal getEffectivePrice() {
    return "VND".equalsIgnoreCase(currency) ? pricePerOccurrenceVnd : pricePerOccurrenceUsd;
  }

  public UUID getId() {
    return id;
  }

  public UUID getRequestId() {
    return requestId;
  }

  public int getVersionNo() {
    return versionNo;
  }

  public String getScopeSnapshot() {
    return scopeSnapshot;
  }

  public Instant getFirstOccurrenceStartAt() {
    return firstOccurrenceStartAt;
  }

  public Instant getFirstOccurrenceEndAt() {
    return firstOccurrenceEndAt;
  }

  public UUID getInspectorId() {
    return inspectorId;
  }

  public UUID getDroneId() {
    return droneId;
  }

  public String getCurrency() {
    return currency;
  }

  public BigDecimal getPricePerOccurrenceVnd() {
    return pricePerOccurrenceVnd;
  }

  public BigDecimal getPricePerOccurrenceUsd() {
    return pricePerOccurrenceUsd;
  }

  public UUID getIssuedByUserId() {
    return issuedByUserId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
