package com.smartdroneinspection.maintenance.domain;

import com.smartdroneinspection.maintenance.domain.enums.CostLineKind;
import com.smartdroneinspection.maintenance.domain.enums.CostLineState;
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
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One priced line of an estimate.
 *
 * <p>The amount is always derived, never supplied: MF4-07 requires the SYSTEM to calculate totals
 * with decimal arithmetic. A line without a unit rate is rejected rather than stored as zero — a
 * missing price is unknown, not free.
 */
@Entity
@Table(name = "maintenance_cost_lines")
public class MaintenanceCostLine {

  private static final int RATE_SCALE = 6;
  private static final int AMOUNT_SCALE = 2;

  @Id @GeneratedValue private UUID id;

  @Column(name = "work_order_id", nullable = false)
  private UUID workOrderId;

  @Column(name = "estimate_version_id")
  private UUID estimateVersionId;

  @Column(name = "task_id")
  private UUID taskId;

  @Enumerated(EnumType.STRING)
  @Column(name = "line_kind", nullable = false, length = 24)
  private CostLineKind lineKind;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 24)
  private CostLineState state;

  @Column(nullable = false, length = 1000)
  private String description;

  @Column(nullable = false, precision = 18, scale = RATE_SCALE)
  private BigDecimal quantity = BigDecimal.ONE;

  @Column(length = 48)
  private String unit;

  @Column(name = "unit_rate", nullable = false, precision = 18, scale = RATE_SCALE)
  private BigDecimal unitRate;

  @Column(nullable = false, precision = 18, scale = AMOUNT_SCALE)
  private BigDecimal amount;

  @JdbcTypeCode(SqlTypes.CHAR)
  @Column(nullable = false, length = 3)
  private String currency;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "tax_treatment", columnDefinition = "jsonb")
  private String taxTreatment;

  @Column(name = "evidence_reference", length = 1000)
  private String evidenceReference;

  @Column(name = "entered_by_user_id", nullable = false)
  private UUID enteredByUserId;

  @Column(name = "entered_at", nullable = false, updatable = false)
  private Instant enteredAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected MaintenanceCostLine() {}

  public MaintenanceCostLine(
      UUID workOrderId,
      UUID estimateVersionId,
      UUID taskId,
      CostLineKind lineKind,
      CostLineState state,
      String description,
      BigDecimal quantity,
      String unit,
      BigDecimal unitRate,
      String currency,
      String taxTreatment,
      String evidenceReference,
      UUID enteredByUserId) {
    this.workOrderId = require(workOrderId, "workOrderId");
    this.lineKind = requireKind(lineKind, "lineKind");
    this.state = requireState(state, "state");
    this.description = requireText(description, "description");
    this.quantity = requireQuantity(quantity);
    this.unitRate = requireRate(unitRate);
    this.currency = requireText(currency, "currency");
    this.estimateVersionId = estimateVersionId;
    this.taskId = taskId;
    this.unit = unit;
    this.taxTreatment = taxTreatment;
    this.evidenceReference = evidenceReference;
    this.enteredByUserId = require(enteredByUserId, "enteredByUserId");
    this.amount = computeAmount();
    this.enteredAt = Instant.now();
    this.createdAt = this.enteredAt;
  }

  /** MF4-07: the amount is quantity times unit rate, rounded once at the stored scale. */
  private BigDecimal computeAmount() {
    return quantity.multiply(unitRate).setScale(AMOUNT_SCALE, RoundingMode.HALF_UP);
  }

  /**
   * Binds a line to the estimate version that owns it. This happens once, when the version is
   * persisted; the line is not shared between versions.
   */
  public void attachToEstimate(UUID estimateVersionId) {
    if (this.estimateVersionId != null) {
      throw new IllegalStateException("This cost line already belongs to an estimate version");
    }
    this.estimateVersionId = require(estimateVersionId, "estimateVersionId");
  }

  private static BigDecimal requireQuantity(BigDecimal value) {
    if (value == null || value.signum() < 0) {
      throw new IllegalArgumentException("quantity must be present and not negative");
    }
    return value;
  }

  /**
   * MF4-07: an unpriced line is unknown, not zero. Rejecting here is what keeps a missing price
   * from silently becoming a zero budget line.
   */
  private static BigDecimal requireRate(BigDecimal value) {
    if (value == null) {
      throw new IllegalArgumentException(
          "unitRate is required; an unpriced line cannot be estimated");
    }
    if (value.signum() < 0) {
      throw new IllegalArgumentException("unitRate must not be negative");
    }
    return value;
  }

  private static UUID require(UUID value, String name) {
    if (value == null) {
      throw new IllegalArgumentException(name + " is required");
    }
    return value;
  }

  private static CostLineKind requireKind(CostLineKind value, String name) {
    if (value == null) {
      throw new IllegalArgumentException(name + " is required");
    }
    return value;
  }

  private static CostLineState requireState(CostLineState value, String name) {
    if (value == null) {
      throw new IllegalArgumentException(name + " is required");
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

  public UUID getEstimateVersionId() {
    return estimateVersionId;
  }

  public UUID getTaskId() {
    return taskId;
  }

  public CostLineKind getLineKind() {
    return lineKind;
  }

  public CostLineState getState() {
    return state;
  }

  public String getDescription() {
    return description;
  }

  public BigDecimal getQuantity() {
    return quantity;
  }

  public String getUnit() {
    return unit;
  }

  public BigDecimal getUnitRate() {
    return unitRate;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public String getCurrency() {
    return currency;
  }

  public String getTaxTreatment() {
    return taxTreatment;
  }

  public String getEvidenceReference() {
    return evidenceReference;
  }

  public UUID getEnteredByUserId() {
    return enteredByUserId;
  }

  public Instant getEnteredAt() {
    return enteredAt;
  }
}
