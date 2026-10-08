package com.smartdroneinspection.maintenance.domain;

import com.smartdroneinspection.maintenance.domain.enums.MaintenanceQuotationStatus;
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
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "maintenance_quotations")
public class MaintenanceQuotation {

  @Id @GeneratedValue private UUID id;

  @Column(name = "quotation_series_id", nullable = false)
  private UUID quotationSeriesId;

  @Column(name = "maintenance_ticket_id", nullable = false)
  private UUID maintenanceTicketId;

  @Column(name = "maintenance_assessment_id", nullable = false)
  private UUID maintenanceAssessmentId;

  @Column(name = "version_number", nullable = false)
  private int versionNumber;

  @Column(name = "previous_version_id")
  private UUID previousVersionId;

  @Column(name = "prepared_by_user_id", nullable = false)
  private UUID preparedByUserId;

  @Column(name = "provider_id")
  private UUID providerId;

  @jakarta.persistence.Transient private Integer lockedWarrantyDays;

  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(nullable = false, length = 3)
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
  private MaintenanceQuotationStatus status;

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

  protected MaintenanceQuotation() {}

  public MaintenanceQuotation(
      UUID quotationSeriesId,
      UUID maintenanceTicketId,
      UUID maintenanceAssessmentId,
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
    this.quotationSeriesId = quotationSeriesId;
    this.maintenanceTicketId = maintenanceTicketId;
    this.maintenanceAssessmentId = maintenanceAssessmentId;
    this.versionNumber = versionNumber;
    this.previousVersionId = previousVersionId;
    this.preparedByUserId = preparedByUserId;
    this.currency = currency;
    this.subtotal = subtotal;
    this.taxAmount = taxAmount;
    this.totalAmount = totalAmount;
    this.pricingDetails = pricingDetails;
    this.scopeSnapshot = scopeSnapshot;
    this.estimatedDurationHours = estimatedDurationHours;
    this.paymentTerms = paymentTerms;
    this.status = MaintenanceQuotationStatus.DRAFT;
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public UUID getQuotationSeriesId() {
    return quotationSeriesId;
  }

  public UUID getMaintenanceTicketId() {
    return maintenanceTicketId;
  }

  public UUID getMaintenanceAssessmentId() {
    return maintenanceAssessmentId;
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

  public UUID getProviderId() {
    return providerId;
  }

  public void setProviderId(UUID providerId) {
    this.providerId = providerId;
  }

  public Integer getLockedWarrantyDays() {
    return lockedWarrantyDays;
  }

  public void setLockedWarrantyDays(Integer lockedWarrantyDays) {
    this.lockedWarrantyDays = lockedWarrantyDays;
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

  public MaintenanceQuotationStatus getStatus() {
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

  public void send(Instant sendInstant) {
    this.status = MaintenanceQuotationStatus.SENT;
    this.sentAt = sendInstant;
  }

  public void approve(UUID clientUserId, Instant decisionInstant) {
    this.status = MaintenanceQuotationStatus.APPROVED;
    this.decidedByUserId = clientUserId;
    this.decidedAt = decisionInstant;
  }

  public void reject(UUID clientUserId, String reason, Instant decisionInstant) {
    this.status = MaintenanceQuotationStatus.REJECTED;
    this.decidedByUserId = clientUserId;
    this.revisionReason = reason;
    this.decidedAt = decisionInstant;
  }

  public void supersede() {
    this.status = MaintenanceQuotationStatus.SUPERSEDED;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
