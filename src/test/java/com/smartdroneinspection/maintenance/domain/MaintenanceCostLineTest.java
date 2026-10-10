package com.smartdroneinspection.maintenance.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartdroneinspection.maintenance.domain.enums.CostLineKind;
import com.smartdroneinspection.maintenance.domain.enums.CostLineState;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** MF4-07: SYSTEM derives amounts, and an unpriced line is rejected rather than stored as zero. */
class MaintenanceCostLineTest {

  private static final UUID WORK_ORDER = UUID.randomUUID();
  private static final UUID ESTIMATE = UUID.randomUUID();
  private static final UUID ENGINEER = UUID.randomUUID();

  private static MaintenanceCostLine line(BigDecimal quantity, BigDecimal unitRate) {
    return new MaintenanceCostLine(
        WORK_ORDER,
        ESTIMATE,
        null,
        CostLineKind.LABOR,
        CostLineState.ESTIMATE,
        "Blade replacement",
        quantity,
        "hour",
        unitRate,
        "VND",
        null,
        null,
        ENGINEER);
  }

  @Test
  void computesAmountFromQuantityAndRate() {
    MaintenanceCostLine cost = line(new BigDecimal("3.5"), new BigDecimal("120000"));

    assertThat(cost.getAmount()).isEqualByComparingTo("420000.00");
    assertThat(cost.getAmount().scale()).isEqualTo(2);
  }

  @Test
  void roundsHalfUpAtTheStoredScale() {
    MaintenanceCostLine cost = line(new BigDecimal("1"), new BigDecimal("0.125"));

    assertThat(cost.getAmount()).isEqualByComparingTo("0.13");
  }

  @Test
  void keepsDecimalPrecisionOnRate() {
    MaintenanceCostLine cost = line(new BigDecimal("2"), new BigDecimal("12.345678"));

    assertThat(cost.getUnitRate()).isEqualByComparingTo("12.345678");
    assertThat(cost.getAmount()).isEqualByComparingTo("24.69");
  }

  @Test
  void rejectsMissingUnitRate() {
    assertThatThrownBy(() -> line(new BigDecimal("2"), null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unpriced");
  }

  @Test
  void rejectsNegativeValues() {
    assertThatThrownBy(() -> line(new BigDecimal("-1"), new BigDecimal("5")))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> line(new BigDecimal("1"), new BigDecimal("-5")))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void acceptsAnExplicitZeroRateAsAFreeRecordedItem() {
    MaintenanceCostLine cost = line(new BigDecimal("1"), BigDecimal.ZERO);

    assertThat(cost.getAmount()).isEqualByComparingTo("0.00");
  }

  @Test
  void requiresDescriptionAndCurrency() {
    assertThatThrownBy(
            () ->
                new MaintenanceCostLine(
                    WORK_ORDER,
                    ESTIMATE,
                    null,
                    CostLineKind.LABOR,
                    CostLineState.ESTIMATE,
                    "  ",
                    BigDecimal.ONE,
                    "hour",
                    BigDecimal.TEN,
                    "VND",
                    null,
                    null,
                    ENGINEER))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new MaintenanceCostLine(
                    WORK_ORDER,
                    ESTIMATE,
                    null,
                    CostLineKind.LABOR,
                    CostLineState.ESTIMATE,
                    "Work",
                    BigDecimal.ONE,
                    "hour",
                    BigDecimal.TEN,
                    null,
                    null,
                    null,
                    ENGINEER))
        .isInstanceOf(IllegalArgumentException.class);
  }

  /** An absent quantity is unknown, not one, so it is rejected the same way an absent rate is. */
  @Test
  void rejectsMissingQuantity() {
    assertThatThrownBy(() -> line(null, new BigDecimal("250000")))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("quantity");
  }
}
