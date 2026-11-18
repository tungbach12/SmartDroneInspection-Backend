package com.smartdroneinspection.contracts.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Covers the receivable recovery cap: a recovery cannot exceed what is still outstanding and the
 * recovered total is tracked incrementally.
 */
class InspectorReceivableTest {

  private static final UUID OCCURRENCE_ID = UUID.randomUUID();

  private static InspectorReceivable receivable(BigDecimal assessed) {
    UUID refundId = UUID.randomUUID();
    ServiceRequestOccurrence occurrence = org.mockito.Mockito.mock(ServiceRequestOccurrence.class);
    org.mockito.Mockito.when(occurrence.getId()).thenReturn(OCCURRENCE_ID);
    return new InspectorReceivable(
        refundId, occurrence, UUID.randomUUID(), assessed, "REF-001", "reason", UUID.randomUUID());
  }

  @Test
  @DisplayName("A receivable starts fully outstanding")
  void startsFullyOutstanding() {
    InspectorReceivable r = receivable(new BigDecimal("500000.00"));
    assertThat(r.getAssessedAmount()).isEqualByComparingTo("500000.00");
    assertThat(r.getRecoveredAmount()).isEqualByComparingTo("0.00");
    assertThat(r.getOutstandingAmount()).isEqualByComparingTo("500000.00");
    assertThat(r.getRecoveries()).isEmpty();
  }

  @Test
  @DisplayName("recordRecovery reduces the outstanding amount by exactly the recovered total")
  void recoveryReducesOutstanding() {
    InspectorReceivable r = receivable(new BigDecimal("500000.00"));
    r.recordRecovery(new BigDecimal("200000.00"), "REC-1", "first instalment", UUID.randomUUID());
    assertThat(r.getRecoveredAmount()).isEqualByComparingTo("200000.00");
    assertThat(r.getOutstandingAmount()).isEqualByComparingTo("300000.00");
    assertThat(r.getRecoveries()).hasSize(1);
  }

  @Test
  @DisplayName("A recovery cannot exceed the outstanding amount")
  void recoveryCannotExceedOutstanding() {
    InspectorReceivable r = receivable(new BigDecimal("500000.00"));
    r.recordRecovery(new BigDecimal("400000.00"), "REC-1", "first", UUID.randomUUID());
    assertThatThrownBy(
            () ->
                r.recordRecovery(
                    new BigDecimal("200000.00"), "REC-2", "too much", UUID.randomUUID()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("cannot exceed the outstanding receivable");
  }

  @Test
  @DisplayName("A recovery cannot be zero or negative")
  void recoveryMustBePositive() {
    InspectorReceivable r = receivable(new BigDecimal("500000.00"));
    assertThatThrownBy(() -> r.recordRecovery(BigDecimal.ZERO, "REC-1", "zero", UUID.randomUUID()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Receivable recovery details are required");
    assertThatThrownBy(
            () -> r.recordRecovery(new BigDecimal("-1.00"), "REC-1", "negative", UUID.randomUUID()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Receivable recovery details are required");
  }

  @Test
  @DisplayName("recordRecovery rejects a null or blank reference or note")
  void recoveryRequiresReferenceAndNote() {
    InspectorReceivable r = receivable(new BigDecimal("500000.00"));
    assertThatThrownBy(
            () -> r.recordRecovery(new BigDecimal("100.00"), "  ", "note", UUID.randomUUID()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Receivable recovery details are required");
    assertThatThrownBy(
            () -> r.recordRecovery(new BigDecimal("100.00"), "REC-1", "  ", UUID.randomUUID()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Receivable recovery details are required");
  }

  @Test
  @DisplayName("recordRecovery rejects a null actor")
  void recoveryRequiresActor() {
    InspectorReceivable r = receivable(new BigDecimal("500000.00"));
    assertThatThrownBy(() -> r.recordRecovery(new BigDecimal("100.00"), "REC-1", "note", null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Receivable recovery details are required");
  }

  @Test
  @DisplayName("A recovery cannot exceed the outstanding amount even by a single unit")
  void recoveryCannotExceedByOneUnit() {
    InspectorReceivable r = receivable(new BigDecimal("100.00"));
    assertThatThrownBy(
            () ->
                r.recordRecovery(
                    new BigDecimal("100.01"), "REC-1", "over by a cent", UUID.randomUUID()))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
