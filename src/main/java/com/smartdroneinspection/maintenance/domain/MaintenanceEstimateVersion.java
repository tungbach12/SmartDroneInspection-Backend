package com.smartdroneinspection.maintenance.domain;

import com.smartdroneinspection.maintenance.domain.enums.EstimateStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A versioned estimate for a work order.
 *
 * <p>The lifecycle is DRAFT -> SUBMITTED -> (APPROVED | REJECTED), with SUPERSEDED when a later
 * version replaces an approved one. An approved version is an immutable cost baseline: corrections
 * create a new version rather than editing the approved snapshot, because MF4-08 treats the
 * approved baseline as frozen history.
 *
 * <p>The baseline total is always supplied by the caller from the sum of its priced cost lines — it
 * is never typed in — and MF4-08 forbids the preparer from approving their own estimate.
 */
@Entity
@Table(name = "maintenance_estimate_versions")
public class MaintenanceEstimateVersion {

  private static final int TOTAL_SCALE = 2;

  @Id @GeneratedValue private UUID id;

  @Column(name = "work_order_id", nullable = false)
  private UUID workOrderId;

  @Column(name = "version_no", nullable = false)
  private int versionNo;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private EstimateStatus status;

  @Column(name = "prepared_by_user_id", nullable = false)
  private UUID preparedByUserId;

  @Column(name = "approved_by_user_id")
  private UUID approvedByUserId;

  @Column(name = "approved_at")
  private Instant approvedAt;

  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(nullable = false, length = 3)
  private String currency;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "tax_basis", columnDefinition = "jsonb")
  private String taxBasis;

  @Column(name = "baseline_total", nullable = false, precision = 18, scale = TOTAL_SCALE)
  private BigDecimal baselineTotal;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(columnDefinition = "jsonb")
  private String assumptions;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(nullable = false, columnDefinition = "jsonb")
  private String snapshot;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected MaintenanceEstimateVersion() {}

  public MaintenanceEstimateVersion(
      UUID workOrderId,
      int versionNo,
      UUID preparedByUserId,
      String currency,
      String taxBasis,
      String assumptions,
      String snapshot,
      BigDecimal baselineTotal) {
    this.workOrderId = require(workOrderId, "workOrderId");
    this.versionNo = requirePositive(versionNo, "versionNo");
    this.preparedByUserId = require(preparedByUserId, "preparedByUserId");
    this.currency = requireText(currency, "currency");
    this.taxBasis = taxBasis;
    this.assumptions = assumptions;
    this.snapshot = snapshot == null ? "{}" : snapshot;
    this.baselineTotal = requireTotal(baselineTotal);
    this.status = EstimateStatus.DRAFT;
    this.createdAt = Instant.now();
  }

  /** MF4-07: submission requires every line to carry a quantity and a unit rate. */
  public void submit() {
    if (status != EstimateStatus.DRAFT) {
      throw new IllegalStateException(
          "Estimate version " + versionNo + " is " + status + "; expected DRAFT");
    }
    this.status = EstimateStatus.SUBMITTED;
  }

  /**
   * MF4-08: the approver must not be the preparer. Separation of duties is a domain invariant, not
   * only a service check — no estimate is self-approved.
   */
  public void approve(UUID approvingUserId) {
    if (status != EstimateStatus.SUBMITTED) {
      throw new IllegalStateException(
          "Estimate version " + versionNo + " is " + status + "; expected SUBMITTED");
    }
    UUID approver = require(approvingUserId, "approvingUserId");
    if (approver.equals(preparedByUserId)) {
      throw new IllegalStateException("The estimate preparer cannot approve their own estimate");
    }
    this.approvedByUserId = approver;
    this.approvedAt = Instant.now();
    this.status = EstimateStatus.APPROVED;
  }

  /** MF4-08: a return always carries a reason so the team knows what to revise. */
  public void reject(UUID rejectingUserId, String reason) {
    if (status != EstimateStatus.SUBMITTED) {
      throw new IllegalStateException(
          "Estimate version " + versionNo + " is " + status + "; expected SUBMITTED");
    }
    if (reason == null || reason.isBlank()) {
      throw new IllegalArgumentException("A rejection requires a reason");
    }
    this.approvedByUserId = require(rejectingUserId, "rejectingUserId");
    this.status = EstimateStatus.REJECTED;
  }

  public void supersede() {
    if (status != EstimateStatus.APPROVED) {
      throw new IllegalStateException("Only an approved estimate version can be superseded");
    }
    this.status = EstimateStatus.SUPERSEDED;
  }

  /** True when the baseline is frozen and must not be edited again. */
  public boolean isImmutable() {
    return status == EstimateStatus.APPROVED || status == EstimateStatus.SUPERSEDED;
  }

  /**
   * MF4-07: totals come from decimal arithmetic over the priced lines. Contingency is carried like
   * any other line here; it becomes an incurred cost question only at reconciliation.
   */
  public static BigDecimal totalOf(List<MaintenanceCostLine> lines) {
    if (lines == null || lines.isEmpty()) {
      return BigDecimal.ZERO.setScale(TOTAL_SCALE, RoundingMode.HALF_UP);
    }
    BigDecimal sum = BigDecimal.ZERO;
    for (MaintenanceCostLine line : lines) {
      if (line == null || line.getAmount() == null) {
        throw new IllegalArgumentException("An unpriced line cannot be part of an estimate total");
      }
      sum = sum.add(line.getAmount());
    }
    return sum.setScale(TOTAL_SCALE, RoundingMode.HALF_UP);
  }

  private static BigDecimal requireTotal(BigDecimal value) {
    if (value == null || value.signum() < 0) {
      throw new IllegalArgumentException("baselineTotal must be present and not negative");
    }
    return value.setScale(TOTAL_SCALE, RoundingMode.HALF_UP);
  }

  private static UUID require(UUID value, String name) {
    if (value == null) {
      throw new IllegalArgumentException(name + " is required");
    }
    return value;
  }

  private static int requirePositive(int value, String name) {
    if (value <= 0) {
      throw new IllegalArgumentException(name + " must be positive");
    }
    return value;
  }

  private static String requireText(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(name + " is required");
    }
    return value;
  }

  public UUID getId() {
    return id;
  }

  public UUID getWorkOrderId() {
    return workOrderId;
  }

  public int getVersionNo() {
    return versionNo;
  }

  public EstimateStatus getStatus() {
    return status;
  }

  public UUID getPreparedByUserId() {
    return preparedByUserId;
  }

  public UUID getApprovedByUserId() {
    return approvedByUserId;
  }

  public Instant getApprovedAt() {
    return approvedAt;
  }

  public String getCurrency() {
    return currency;
  }

  public String getTaxBasis() {
    return taxBasis;
  }

  public BigDecimal getBaselineTotal() {
    return baselineTotal;
  }

  public String getAssumptions() {
    return assumptions;
  }

  public String getSnapshot() {
    return snapshot;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
