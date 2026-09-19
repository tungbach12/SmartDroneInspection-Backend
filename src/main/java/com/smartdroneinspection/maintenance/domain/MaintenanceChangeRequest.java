package com.smartdroneinspection.maintenance.domain;

import com.smartdroneinspection.maintenance.domain.enums.ChangeRequestStatus;
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
@Table(name = "maintenance_change_requests")
public class MaintenanceChangeRequest {

  @Id @GeneratedValue private UUID id;

  @Column(name = "maintenance_ticket_id", nullable = false)
  private UUID maintenanceTicketId;

  @Column(name = "work_log_id", nullable = false)
  private UUID workLogId;

  @Column(name = "current_order_id", nullable = false)
  private UUID currentOrderId;

  @Column(name = "requested_by_user_id", nullable = false)
  private UUID requestedByUserId;

  @Column(nullable = false, length = 2000)
  private String reason;

  @Column(name = "additional_scope", nullable = false, length = 4000)
  private String additionalScope;

  @Column(name = "estimated_cost_delta", precision = 14, scale = 2)
  private BigDecimal estimatedCostDelta;

  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(length = 3)
  private String currency;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private ChangeRequestStatus status;

  @Column(name = "decided_by_user_id")
  private UUID decidedByUserId;

  @Column(name = "decided_at")
  private Instant decidedAt;

  @Column(name = "decision_reason", length = 2000)
  private String decisionReason;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "row_version", nullable = false)
  private long rowVersion;

  protected MaintenanceChangeRequest() {}

  public MaintenanceChangeRequest(
      UUID maintenanceTicketId,
      UUID workLogId,
      UUID currentOrderId,
      UUID requestedByUserId,
      String reason,
      String additionalScope) {
    this.maintenanceTicketId = maintenanceTicketId;
    this.workLogId = workLogId;
    this.currentOrderId = currentOrderId;
    this.requestedByUserId = requestedByUserId;
    this.reason = reason;
    this.additionalScope = additionalScope;
    this.status = ChangeRequestStatus.SUBMITTED;
    this.createdAt = Instant.now();
    this.updatedAt = createdAt;
  }

  public UUID getId() {
    return id;
  }

  public UUID getMaintenanceTicketId() {
    return maintenanceTicketId;
  }

  public ChangeRequestStatus getStatus() {
    return status;
  }
}
