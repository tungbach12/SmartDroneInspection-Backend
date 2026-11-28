package com.smartdroneinspection.contracts.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Decimal arithmetic for inspection service financial breakdown.
 *
 * <p>Customer price is VAT-inclusive. The agreed total is separated into pre-VAT service base and
 * VAT portion. Platform commission is calculated exclusively on the pre-VAT service base.
 *
 * <p><strong>MVP ASSUMPTION NOTICE FOR ACCOUNTANT REVIEW:</strong> The platform applies the
 * configured TNCN rate to the Inspector gross payout (defined as pre-VAT service base minus
 * platform commission) before TNCN deduction. Base, rate, and withholding amount are snapshotted
 * per occurrence. This is a simplified product assumption for MVP workflow and accounting
 * validation, NOT statutory tax advice or a legal conclusion on individual contractor tax
 * liability.
 */
public final class InspectionFinanceCalculator {

  private InspectionFinanceCalculator() {}

  public static FinanceCalculation calculate(
      BigDecimal totalPriceInclusiveVat,
      BigDecimal vatRatePercent,
      BigDecimal commissionRatePercent,
      BigDecimal tncnRatePercent) {
    return calculate(
        totalPriceInclusiveVat, vatRatePercent, commissionRatePercent, tncnRatePercent, null);
  }

  public static FinanceCalculation calculate(
      BigDecimal totalPriceInclusiveVat,
      BigDecimal vatRatePercent,
      BigDecimal commissionRatePercent,
      BigDecimal tncnRatePercent,
      BigDecimal tncnThresholdAmount) {
    if (totalPriceInclusiveVat == null || totalPriceInclusiveVat.signum() <= 0) {
      throw new IllegalArgumentException("Total price must be positive");
    }
    if (vatRatePercent == null || commissionRatePercent == null || tncnRatePercent == null) {
      throw new IllegalArgumentException(
          "All settlement rates (VAT, commission, TNCN) are required");
    }
    if (tncnThresholdAmount != null && tncnThresholdAmount.compareTo(BigDecimal.ZERO) < 0) {
      throw new IllegalArgumentException("TNCN threshold amount must not be negative");
    }

    BigDecimal vatRatio = vatRatePercent.divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);
    BigDecimal commissionRatio =
        commissionRatePercent.divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);
    BigDecimal tncnRatio = tncnRatePercent.divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);

    // Pre-VAT service base = total / (1 + vatRatio)
    BigDecimal serviceBaseAmount =
        totalPriceInclusiveVat.divide(BigDecimal.ONE.add(vatRatio), 2, RoundingMode.HALF_UP);

    // VAT amount = total - pre-VAT base (guarantees exact sum without fractional drift)
    BigDecimal vatAmount = totalPriceInclusiveVat.subtract(serviceBaseAmount);

    // Platform commission is calculated on pre-VAT service base
    BigDecimal commissionAmount =
        serviceBaseAmount.multiply(commissionRatio).setScale(2, RoundingMode.HALF_UP);

    // MVP tax base: Inspector gross compensation after platform commission and before TNCN
    BigDecimal inspectorGrossPayout = serviceBaseAmount.subtract(commissionAmount);

    BigDecimal normalizedThreshold =
        tncnThresholdAmount == null || tncnThresholdAmount.signum() <= 0
            ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
            : tncnThresholdAmount.setScale(2, RoundingMode.HALF_UP);
    boolean shouldWithholdTncn =
        normalizedThreshold.signum() == 0
            || inspectorGrossPayout.compareTo(normalizedThreshold) >= 0;

    // TNCN (PIT) withholding calculated against Inspector gross compensation when >= threshold
    BigDecimal tncnAmount =
        shouldWithholdTncn
            ? inspectorGrossPayout.multiply(tncnRatio).setScale(2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

    // Inspector Net Payout = gross payout minus TNCN withheld
    BigDecimal inspectorNetPayout = inspectorGrossPayout.subtract(tncnAmount);

    return new FinanceCalculation(
        totalPriceInclusiveVat.setScale(2, RoundingMode.HALF_UP),
        serviceBaseAmount,
        vatRatePercent.setScale(2, RoundingMode.HALF_UP),
        vatAmount,
        commissionRatePercent.setScale(2, RoundingMode.HALF_UP),
        commissionAmount,
        inspectorGrossPayout,
        tncnRatePercent.setScale(2, RoundingMode.HALF_UP),
        tncnAmount,
        inspectorNetPayout,
        normalizedThreshold);
  }

  public record FinanceCalculation(
      BigDecimal totalPrice,
      BigDecimal serviceBaseAmount,
      BigDecimal vatRate,
      BigDecimal vatAmount,
      BigDecimal commissionRate,
      BigDecimal commissionAmount,
      BigDecimal inspectorGrossPayout,
      BigDecimal tncnRate,
      BigDecimal tncnAmount,
      BigDecimal inspectorNetPayout,
      BigDecimal tncnThresholdAmount) {

    public FinanceCalculation(
        BigDecimal totalPrice,
        BigDecimal serviceBaseAmount,
        BigDecimal vatRate,
        BigDecimal vatAmount,
        BigDecimal commissionRate,
        BigDecimal commissionAmount,
        BigDecimal inspectorGrossPayout,
        BigDecimal tncnRate,
        BigDecimal tncnAmount,
        BigDecimal inspectorNetPayout) {
      this(
          totalPrice,
          serviceBaseAmount,
          vatRate,
          vatAmount,
          commissionRate,
          commissionAmount,
          inspectorGrossPayout,
          tncnRate,
          tncnAmount,
          inspectorNetPayout,
          BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
    }
  }
}
