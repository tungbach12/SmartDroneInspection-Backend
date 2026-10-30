package com.smartdroneinspection.contracts.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InspectionFinanceCalculatorTest {

  @Test
  @DisplayName("VND VAT split, commission on pre-VAT base, and MVP TNCN tax base calculation")
  void calculatesVatInclusiveSplitAndCommissionAndTncnWithholding() {
    // Customer pays 11,000,000 VND inclusive of 10% VAT
    BigDecimal totalPrice = new BigDecimal("11000000.00");
    BigDecimal vatRate = new BigDecimal("10.00");
    BigDecimal commissionRate = new BigDecimal("15.00");
    BigDecimal tncnRate = new BigDecimal("10.00");

    var calc = InspectionFinanceCalculator.calculate(totalPrice, vatRate, commissionRate, tncnRate);

    // Pre-VAT service base = 11,000,000 / 1.10 = 10,000,000.00
    assertThat(calc.serviceBaseAmount()).isEqualByComparingTo("10000000.00");
    // VAT amount = 11,000,000 - 10,000,000 = 1,000,000.00
    assertThat(calc.vatAmount()).isEqualByComparingTo("1000000.00");
    assertThat(calc.serviceBaseAmount().add(calc.vatAmount())).isEqualByComparingTo(totalPrice);

    // Platform commission is 15% on pre-VAT service base = 1,500,000.00
    assertThat(calc.commissionAmount()).isEqualByComparingTo("1500000.00");

    // MVP tax base: Inspector gross payout after platform commission and before TNCN = 8,500,000.00
    assertThat(calc.inspectorGrossPayout()).isEqualByComparingTo("8500000.00");
    assertThat(calc.commissionAmount().add(calc.inspectorGrossPayout()))
        .isEqualByComparingTo(calc.serviceBaseAmount());

    // TNCN (PIT) withholding at 10% on Inspector gross compensation = 850,000.00
    assertThat(calc.tncnAmount()).isEqualByComparingTo("850000.00");

    // Inspector net payout = 8,500,000 - 850,000 = 7,650,000.00
    assertThat(calc.inspectorNetPayout()).isEqualByComparingTo("7650000.00");
    assertThat(calc.tncnAmount().add(calc.inspectorNetPayout()))
        .isEqualByComparingTo(calc.inspectorGrossPayout());

    // Full system reconciliation: VAT + Commission + TNCN + Inspector Net = Customer Total
    BigDecimal systemSum =
        calc.vatAmount()
            .add(calc.commissionAmount())
            .add(calc.tncnAmount())
            .add(calc.inspectorNetPayout());
    assertThat(systemSum).isEqualByComparingTo(totalPrice);
  }

  @Test
  @DisplayName("Handles decimal rounding without fractional leakage")
  void handlesDecimalRoundingSafely() {
    BigDecimal totalPrice = new BigDecimal("5555555.00");
    BigDecimal vatRate = new BigDecimal("8.00");
    BigDecimal commissionRate = new BigDecimal("12.50");
    BigDecimal tncnRate = new BigDecimal("7.00");

    var calc = InspectionFinanceCalculator.calculate(totalPrice, vatRate, commissionRate, tncnRate);

    // Exact reconciliation
    assertThat(calc.serviceBaseAmount().add(calc.vatAmount())).isEqualByComparingTo(totalPrice);
    assertThat(calc.commissionAmount().add(calc.inspectorGrossPayout()))
        .isEqualByComparingTo(calc.serviceBaseAmount());
    assertThat(calc.tncnAmount().add(calc.inspectorNetPayout()))
        .isEqualByComparingTo(calc.inspectorGrossPayout());
  }

  @Test
  @DisplayName("Rejects null or non-positive total price")
  void rejectsInvalidTotalPrice() {
    assertThatThrownBy(
            () ->
                InspectionFinanceCalculator.calculate(
                    BigDecimal.ZERO,
                    new BigDecimal("10.00"),
                    new BigDecimal("10.00"),
                    new BigDecimal("10.00")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Total price must be positive");

    assertThatThrownBy(
            () ->
                InspectionFinanceCalculator.calculate(
                    null,
                    new BigDecimal("10.00"),
                    new BigDecimal("10.00"),
                    new BigDecimal("10.00")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("Rejects missing settlement rates")
  void rejectsMissingRates() {
    BigDecimal price = new BigDecimal("10000000.00");
    assertThatThrownBy(
            () ->
                InspectionFinanceCalculator.calculate(
                    price, null, new BigDecimal("10.00"), new BigDecimal("10.00")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("All settlement rates");
  }
}
