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

  public UUID getMaintenanceTicketId() {
    return maintenanceTicketId;
  }

  public MaintenanceOrderStatus getStatus() {
    return status;
  }
}
