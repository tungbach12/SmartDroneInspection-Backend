package com.smartdroneinspection.contracts.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Inspection service settlement rates configured by Admin.
 *
 * <p>Exactly three independent rates are maintained:
 *
 * <ul>
 *   <li>Inspection commission rate (percentage deducted from pre-VAT service base)
 *   <li>VAT rate (percentage included in Customer total and separated from pre-VAT base)
 *   <li>TNCN (PIT) withholding rate (percentage applied to Inspector payout)
 * </ul>
 *
 * <p><strong>MVP ASSUMPTION NOTICE FOR ACCOUNTANT REVIEW:</strong> The platform applies the
 * configured TNCN rate to the Inspector gross payout (defined as pre-VAT service base minus
 * platform commission) before TNCN deduction. Base, rate, and withholding amount are snapshotted
 * per occurrence. This is a simplified product assumption for MVP workflow and accounting
 * validation, NOT statutory tax advice or a legal conclusion on individual contractor tax
 * liability.
 */
@Entity
@Table(name = "inspection_finance_settings")
public class InspectionFinanceSettings {

  public static final BigDecimal DEFAULT_TNCN_THRESHOLD_AMOUNT = new BigDecimal("2000000.00");

  @Id @GeneratedValue private UUID id;

  @Column(name = "commission_rate", precision = 5, scale = 2)
  private BigDecimal commissionRate;

  @Column(name = "vat_rate", precision = 5, scale = 2)
  private BigDecimal vatRate;

  @Column(name = "tncn_rate", precision = 5, scale = 2)
  private BigDecimal tncnRate;

  @Column(name = "tncn_threshold_amount", nullable = false, precision = 15, scale = 2)
  private BigDecimal tncnThresholdAmount = DEFAULT_TNCN_THRESHOLD_AMOUNT;

  @Column(name = "updated_by_user_id")
  private UUID updatedByUserId;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt = Instant.now();

  protected InspectionFinanceSettings() {}

  public InspectionFinanceSettings(
      BigDecimal commissionRate, BigDecimal vatRate, BigDecimal tncnRate, UUID updatedByUserId) {
    this(commissionRate, vatRate, tncnRate, DEFAULT_TNCN_THRESHOLD_AMOUNT, updatedByUserId);
  }

  public InspectionFinanceSettings(
      BigDecimal commissionRate,
      BigDecimal vatRate,
      BigDecimal tncnRate,
      BigDecimal tncnThresholdAmount,
      UUID updatedByUserId) {
    updateRates(commissionRate, vatRate, tncnRate, tncnThresholdAmount, updatedByUserId);
  }

  public void updateRates(
      BigDecimal commissionRate, BigDecimal vatRate, BigDecimal tncnRate, UUID updatedByUserId) {
    updateRates(commissionRate, vatRate, tncnRate, null, updatedByUserId);
  }

  public void updateRates(
      BigDecimal commissionRate,
      BigDecimal vatRate,
      BigDecimal tncnRate,
      BigDecimal tncnThresholdAmount,
      UUID updatedByUserId) {
    validateRate("commissionRate", commissionRate);
    validateRate("vatRate", vatRate);
    validateRate("tncnRate", tncnRate);
    BigDecimal resolvedThreshold =
        tncnThresholdAmount != null
            ? tncnThresholdAmount
            : (this.tncnThresholdAmount != null
                ? this.tncnThresholdAmount
                : DEFAULT_TNCN_THRESHOLD_AMOUNT);
    if (resolvedThreshold.compareTo(BigDecimal.ZERO) < 0) {
      throw new IllegalArgumentException("tncnThresholdAmount must be greater than or equal to 0");
    }
    this.commissionRate = commissionRate.setScale(2, java.math.RoundingMode.HALF_UP);
    this.vatRate = vatRate.setScale(2, java.math.RoundingMode.HALF_UP);
    this.tncnRate = tncnRate.setScale(2, java.math.RoundingMode.HALF_UP);
    this.tncnThresholdAmount = resolvedThreshold.setScale(2, java.math.RoundingMode.HALF_UP);
    this.updatedByUserId = updatedByUserId;
    this.updatedAt = Instant.now();
  }

  public boolean isFullyConfigured() {
    return commissionRate != null
        && vatRate != null
        && tncnRate != null
        && tncnThresholdAmount != null;
  }

  private static void validateRate(String name, BigDecimal rate) {
    if (rate == null) {
      throw new IllegalArgumentException(name + " is required");
    }
    if (rate.compareTo(BigDecimal.ZERO) < 0 || rate.compareTo(new BigDecimal("100.00")) > 0) {
      throw new IllegalArgumentException(name + " must be between 0 and 100 percent");
    }
  }

  public UUID getId() {
    return id;
  }

  public BigDecimal getCommissionRate() {
    return commissionRate;
  }

  public BigDecimal getVatRate() {
    return vatRate;
  }

  public BigDecimal getTncnRate() {
    return tncnRate;
  }

  public BigDecimal getTncnThresholdAmount() {
    return tncnThresholdAmount;
  }

  public UUID getUpdatedByUserId() {
    return updatedByUserId;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
