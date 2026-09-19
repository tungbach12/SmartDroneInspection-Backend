package com.smartdroneinspection.inspectionrequests.domain;

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
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "inspection_quotations")
public class InspectionQuotation {

  @Id @GeneratedValue private UUID id;

  @Column(name = "quotation_series_id", nullable = false)
  private UUID quotationSeriesId;

  @Column(name = "inspection_request_id", nullable = false)
  private UUID inspectionRequestId;

  @Column(name = "version_number", nullable = false)
  private int versionNumber;

  @Column(name = "previous_version_id")
  private UUID previousVersionId;

  @Column(name = "prepared_by_user_id", nullable = false)
  private UUID preparedByUserId;

  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(nullable = false, length = 3, columnDefinition = "char(3)")
  private String currency;

  @Column(nullable = false, precision = 14, scale = 2)
  private BigDecimal subtotal;

  @Column(name = "tax_amount", nullable = false, precision = 14, scale = 2)
  private BigDecimal taxAmount;

  @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
  private BigDecimal totalAmount;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "pricing_details", nullable = false, columnDefinition = "jsonb")
  private String pricingDetails;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "scope_snapshot", nullable = false, columnDefinition = "jsonb")
  private String scopeSnapshot;

  @Column(name = "estimated_duration_hours", precision = 10, scale = 2)
  private BigDecimal estimatedDurationHours;

  @Column(name = "payment_terms", nullable = false, length = 2000)
  private String paymentTerms;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private InspectionQuotationStatus status;

  @Column(name = "sent_at")
  private Instant sentAt;

  @Column(name = "decided_by_user_id")
  private UUID decidedByUserId;

  @Column(name = "decided_at")
  private Instant decidedAt;

  @Column(name = "revision_reason", length = 2000)
  private String revisionReason;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Version
  @Column(name = "row_version", nullable = false)
  private long rowVersion;

  protected InspectionQuotation() {}

  public InspectionQuotation(
      UUID quotationSeriesId,
      UUID inspectionRequestId,
      int versionNumber,
      UUID previousVersionId,
      UUID preparedByUserId,
      String currency,
      BigDecimal subtotal,
      BigDecimal taxAmount,
      BigDecimal totalAmount,
      String pricingDetails,
      String scopeSnapshot,
      BigDecimal estimatedDurationHours,
      String paymentTerms) {
    this.quotationSeriesId =
        Objects.requireNonNull(quotationSeriesId, "Quotation series is required");
    this.inspectionRequestId =
        Objects.requireNonNull(inspectionRequestId, "Inspection request is required");
    if (versionNumber <= 0) {
      throw new IllegalArgumentException("Quotation version must be positive");
    }
    this.versionNumber = versionNumber;
    this.previousVersionId = previousVersionId;
    this.preparedByUserId = Objects.requireNonNull(preparedByUserId, "Preparer is required");
    this.currency = normalizeCurrency(currency);
    this.subtotal = validateAmount(subtotal);
    this.taxAmount = validateAmount(taxAmount);
    this.totalAmount = validateAmount(totalAmount);
    if (pricingDetails == null || pricingDetails.isBlank()) {
      throw new IllegalArgumentException("Pricing details must not be blank");
    }
    if (scopeSnapshot == null || scopeSnapshot.isBlank()) {
      throw new IllegalArgumentException("Scope snapshot must not be blank");
    }
    this.pricingDetails = pricingDetails;
    this.scopeSnapshot = scopeSnapshot;
    if (estimatedDurationHours != null && estimatedDurationHours.signum() <= 0) {
      throw new IllegalArgumentException("Estimated duration must be positive");
    }
    this.estimatedDurationHours = estimatedDurationHours;
    if (paymentTerms == null || paymentTerms.isBlank()) {
      throw new IllegalArgumentException("Payment terms must not be blank");
    }
    this.paymentTerms = paymentTerms;
    this.status = InspectionQuotationStatus.DRAFT;
    this.createdAt = Instant.now();
  }

  public void send() {
    ensureStatus(InspectionQuotationStatus.DRAFT, InspectionQuotationStatus.REVISION_REQUESTED);
    status = InspectionQuotationStatus.SENT;
    sentAt = Instant.now();
  }

  public void requestRevision(String reason) {
    ensureStatus(InspectionQuotationStatus.SENT);
    if (reason == null || reason.isBlank()) {
      throw new IllegalArgumentException("Revision reason must not be blank");
    }
    status = InspectionQuotationStatus.REVISION_REQUESTED;
    revisionReason = reason;
  }

  public void approve(UUID clientUserId) {
    decide(InspectionQuotationStatus.APPROVED, clientUserId);
  }

  public void reject(UUID clientUserId) {
    decide(InspectionQuotationStatus.REJECTED, clientUserId);
  }

  public void supersede() {
    ensureStatus(
        InspectionQuotationStatus.DRAFT,
        InspectionQuotationStatus.SENT,
        InspectionQuotationStatus.REVISION_REQUESTED);
    status = InspectionQuotationStatus.SUPERSEDED;
  }

  public UUID getId() {
    return id;
  }

  public UUID getQuotationSeriesId() {
    return quotationSeriesId;
  }

  public UUID getInspectionRequestId() {
    return inspectionRequestId;
  }

  public int getVersionNumber() {
    return versionNumber;
  }

  public UUID getPreviousVersionId() {
    return previousVersionId;
  }

  public UUID getPreparedByUserId() {
    return preparedByUserId;
  }

  public String getCurrency() {
    return currency;
  }

  public BigDecimal getSubtotal() {
    return subtotal;
  }

  public BigDecimal getTaxAmount() {
    return taxAmount;
  }

  public BigDecimal getTotalAmount() {
    return totalAmount;
  }

  public String getPricingDetails() {
    return pricingDetails;
  }

  public String getScopeSnapshot() {
    return scopeSnapshot;
  }

  public BigDecimal getEstimatedDurationHours() {
    return estimatedDurationHours;
  }

  public String getPaymentTerms() {
    return paymentTerms;
  }

  public InspectionQuotationStatus getStatus() {
    return status;
  }

  public Instant getSentAt() {
    return sentAt;
  }

  public UUID getDecidedByUserId() {
    return decidedByUserId;
  }

  public Instant getDecidedAt() {
    return decidedAt;
  }

  public String getRevisionReason() {
    return revisionReason;
  }

  private void decide(InspectionQuotationStatus decision, UUID clientUserId) {
    ensureStatus(InspectionQuotationStatus.SENT, InspectionQuotationStatus.REVISION_REQUESTED);
    decidedByUserId = Objects.requireNonNull(clientUserId, "Decision actor is required");
    decidedAt = Instant.now();
    status = decision;
  }

  private void ensureStatus(InspectionQuotationStatus... allowed) {
    for (InspectionQuotationStatus candidate : allowed) {
      if (status == candidate) {
        return;
      }
    }
    throw new IllegalStateException("Invalid quotation status transition from " + status);
  }

  private static BigDecimal validateAmount(BigDecimal amount) {
    Objects.requireNonNull(amount, "Quotation amount is required");
    if (amount.signum() < 0) {
      throw new IllegalArgumentException("Quotation amounts must not be negative");
    }
    return amount;
  }

  private static String normalizeCurrency(String value) {
    if (value == null || !value.trim().matches("[A-Za-z]{3}")) {
      throw new IllegalArgumentException("Currency must be a three-letter code");
    }
    return value.trim().toUpperCase(Locale.ROOT);
  }
}
