package com.smartdroneinspection.maintenance.domain;

import com.smartdroneinspection.maintenance.domain.enums.MaintenanceOrderStatus;
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
@Table(name = "maintenance_orders")
public class MaintenanceOrder {

  @Id @GeneratedValue private UUID id;

  @Column(name = "order_series_id", nullable = false)
  private UUID orderSeriesId;

  @Column(name = "order_number", nullable = false, length = 64)
  private String orderNumber;

  @Column(name = "maintenance_ticket_id", nullable = false)
  private UUID maintenanceTicketId;

  @Column(name = "approved_quotation_id")
  private UUID approvedQuotationId;

  @Column(name = "change_request_id")
  private UUID changeRequestId;

  @Column(name = "version_number", nullable = false)
  private int versionNumber;

  @Column(name = "previous_version_id")
  private UUID previousVersionId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "scope_snapshot", nullable = false, columnDefinition = "jsonb")
  private String scopeSnapshot;

  @Column(name = "approved_amount", nullable = false, precision = 14, scale = 2)
  private BigDecimal approvedAmount;

  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(nullable = false, length = 3)
  private String currency;

  @Column(name = "payment_terms", nullable = false, length = 2000)
  private String paymentTerms;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private MaintenanceOrderStatus status;

  @Column(name = "approved_by_user_id", nullable = false)
  private UUID approvedByUserId;

  @Column(name = "approved_at", nullable = false)
  private Instant approvedAt;

  @Column(name = "provider_id")
  private UUID providerId;

  @Column(name = "locked_warranty_days")
  private Integer lockedWarrantyDays;

  @Column(name = "warranty_end_date")
  private Instant warrantyEndDate;

  @Column(name = "payment_invoice_issued_at")
  private Instant paymentInvoiceIssuedAt;

  @Column(name = "paid_at")
  private Instant paidAt;

  @Column(name = "provider_bank_account_number", length = 64)
  private String providerBankAccountNumber;

  @Column(name = "provider_bank_name", length = 128)
  private String providerBankName;

  @Column(name = "started_at")
  private Instant startedAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "row_version", nullable = false)
  private long rowVersion;

  protected MaintenanceOrder() {}

  public MaintenanceOrder(
      UUID orderSeriesId,
      String orderNumber,
      UUID maintenanceTicketId,
      UUID approvedQuotationId,
      UUID changeRequestId,
      int versionNumber,
      UUID previousVersionId,
      String scopeSnapshot,
      BigDecimal approvedAmount,
      String currency,
      String paymentTerms,
      UUID approvedByUserId,
      Instant approvedAt) {
    this.orderSeriesId = orderSeriesId;
    this.orderNumber = orderNumber;
    this.maintenanceTicketId = maintenanceTicketId;
    this.approvedQuotationId = approvedQuotationId;
    this.changeRequestId = changeRequestId;
    this.versionNumber = versionNumber;
    this.previousVersionId = previousVersionId;
    this.scopeSnapshot = scopeSnapshot;
    this.approvedAmount = approvedAmount;
    this.currency = currency;
    this.paymentTerms = paymentTerms;
    this.status = MaintenanceOrderStatus.CONFIRMED;
    this.approvedByUserId = approvedByUserId;
    this.approvedAt = approvedAt;
    this.createdAt = Instant.now();
    this.updatedAt = createdAt;
  }

  public UUID getId() {
    return id;
  }

  public UUID getOrderSeriesId() {
    return orderSeriesId;
  }

  public String getOrderNumber() {
    return orderNumber;
  }

  public UUID getMaintenanceTicketId() {
    return maintenanceTicketId;
  }

  public UUID getApprovedQuotationId() {
    return approvedQuotationId;
  }

  public UUID getChangeRequestId() {
    return changeRequestId;
  }

  public int getVersionNumber() {
    return versionNumber;
  }

  public UUID getPreviousVersionId() {
    return previousVersionId;
  }

  public String getScopeSnapshot() {
    return scopeSnapshot;
  }

  public BigDecimal getApprovedAmount() {
    return approvedAmount;
  }

  public String getCurrency() {
    return currency;
  }

  public String getPaymentTerms() {
    return paymentTerms;
  }

  public MaintenanceOrderStatus getStatus() {
    return status;
  }

  public UUID getApprovedByUserId() {
    return approvedByUserId;
  }

  public Instant getApprovedAt() {
    return approvedAt;
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

  public Instant getWarrantyEndDate() {
    return warrantyEndDate;
  }

  public void setWarrantyEndDate(Instant warrantyEndDate) {
    this.warrantyEndDate = warrantyEndDate;
  }

  public Instant getPaymentInvoiceIssuedAt() {
    return paymentInvoiceIssuedAt;
  }

  public void setPaymentInvoiceIssuedAt(Instant paymentInvoiceIssuedAt) {
    this.paymentInvoiceIssuedAt = paymentInvoiceIssuedAt;
  }

  public Instant getPaidAt() {
    return paidAt;
  }

  public String getProviderBankAccountNumber() {
    return providerBankAccountNumber;
  }

  public void setProviderBankAccountNumber(String providerBankAccountNumber) {
    this.providerBankAccountNumber = providerBankAccountNumber;
  }

  public String getProviderBankName() {
    return providerBankName;
  }

  public void setProviderBankName(String providerBankName) {
    this.providerBankName = providerBankName;
  }

  public Instant getStartedAt() {
    return startedAt;
  }

  public Instant getCompletedAt() {
    return completedAt;
  }

  public void markInProgress() {
    if (this.status != MaintenanceOrderStatus.CONFIRMED) {
      throw new IllegalStateException("Only confirmed orders can move to in progress");
    }
    this.status = MaintenanceOrderStatus.IN_PROGRESS;
    this.startedAt = Instant.now();
    this.updatedAt = startedAt;
  }

  public void markAwaitingPayment(Instant completionInstant, Instant warrantyEnd) {
    this.status = MaintenanceOrderStatus.AWAITING_PAYMENT;
    this.completedAt = completionInstant;
    this.warrantyEndDate = warrantyEnd;
    this.updatedAt = completionInstant;
  }

  public void markPaid(Instant paidAt) {
    this.status = MaintenanceOrderStatus.PAID;
    this.paidAt = paidAt;
    this.updatedAt = paidAt;
  }

  public void markSuperseded() {
    this.status = MaintenanceOrderStatus.SUPERSEDED;
    this.updatedAt = Instant.now();
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
