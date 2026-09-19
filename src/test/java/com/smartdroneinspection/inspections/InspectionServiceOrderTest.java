package com.smartdroneinspection.inspections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartdroneinspection.inspections.domain.InspectionOrderStatus;
import com.smartdroneinspection.inspections.domain.InspectionQuotation;
import com.smartdroneinspection.inspections.domain.InspectionServiceOrder;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InspectionServiceOrderTest {

  @Test
  void serviceOrderRequiresApprovedQuotation() {
    InspectionQuotation quotation = newQuotation();

    assertThatThrownBy(
            () ->
                InspectionServiceOrder.fromApprovedQuotation(
                    quotation, "SO-2026-0001", UUID.randomUUID(), "Deliverables"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("Only approved quotations can create service orders");
  }

  @Test
  void approvedQuotationCreatesConfirmedOrderAndCanAwaitAssignment() {
    InspectionQuotation quotation = newQuotation();
    quotation.send();
    quotation.approve(UUID.randomUUID());

    InspectionServiceOrder order =
        InspectionServiceOrder.fromApprovedQuotation(
            quotation, "SO-2026-0001", UUID.randomUUID(), "Final inspection report");

    assertThat(order.getStatus()).isEqualTo(InspectionOrderStatus.CONFIRMED);
    assertThat(order.getApprovedQuotationId()).isEqualTo(quotation.getId());
    assertThat(order.getInspectionRequestId()).isEqualTo(quotation.getInspectionRequestId());

    order.markAssignmentPending();

    assertThat(order.getStatus()).isEqualTo(InspectionOrderStatus.ASSIGNMENT_PENDING);
  }

  private InspectionQuotation newQuotation() {
    return new InspectionQuotation(
        UUID.randomUUID(),
        UUID.randomUUID(),
        1,
        null,
        UUID.randomUUID(),
        "USD",
        BigDecimal.valueOf(100),
        BigDecimal.ZERO,
        BigDecimal.valueOf(100),
        "{\"items\":[]}",
        "{\"scope\":\"tower\"}",
        BigDecimal.ONE,
        "NET 30");
  }
}
