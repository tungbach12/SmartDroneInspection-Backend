package com.smartdroneinspection.contracts.api.dto.response;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.contracts.domain.InspectorReceivable;
import com.smartdroneinspection.contracts.domain.ServiceRefund;
import com.smartdroneinspection.contracts.domain.ServiceRequestOccurrence;
import com.smartdroneinspection.contracts.repository.InspectorReceivableRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class OccurrenceFinanceResponseTest {

  private static final BigDecimal TOTAL = new BigDecimal("1000000.00");

  @Test
  void managerViewIncludesRefundsReceivablesAndPayoutBreakdown() {
    Fixture fixture = paidFixture();

    OccurrenceFinanceResponse response =
        OccurrenceFinanceResponse.forManager(fixture.occurrence(), fixture.receivables());

    assertThat(response.occurrenceId()).isEqualTo(fixture.occurrence().getId());
    assertThat(response.currency()).isEqualTo("VND");
    assertThat(response.commissionAmount()).isNotNull();
    assertThat(response.inspectorNetPayout()).isNotNull();
    assertThat(response.paymentStatus())
        .isEqualTo(ServiceRequestOccurrence.OccurrencePaymentStatus.PAID);
    assertThat(response.payoutStatus())
        .isEqualTo(ServiceRequestOccurrence.OccurrencePayoutStatus.PAID);
    assertThat(response.refunds()).hasSize(1);
    assertThat(response.refunds().getFirst().status()).isEqualTo("COMPLETED");
    assertThat(response.inspectorReceivables()).hasSize(1);
    assertThat(response.inspectorReceivables().getFirst().recoveries()).hasSize(1);
    assertThat(response.accountingDisclaimer()).contains("MVP assumption");
    assertThat(response.invoice().totalPrice()).isEqualByComparingTo(TOTAL);
    assertThat(response.payment().method()).isEqualTo("PAYOS");
    assertThat(response.payout().heldForRefund()).isFalse();
  }

  @Test
  void customerViewMasksInternalCommissionTaxAndPayoutDetails() {
    Fixture fixture = paidFixture();

    OccurrenceFinanceResponse response =
        OccurrenceFinanceResponse.forCustomer(fixture.occurrence());

    assertThat(response.commissionRate()).isNull();
    assertThat(response.commissionAmount()).isNull();
    assertThat(response.inspectorNetPayout()).isNull();
    assertThat(response.inspectorReceivables()).isEmpty();
    assertThat(response.payout()).isNull();
    assertThat(response.accountingDisclaimer()).isNull();
    assertThat(response.invoice().commissionRate()).isNull();
    assertThat(response.invoice().tncnRate()).isNull();
    assertThat(response.payment().reference()).isNull();
    assertThat(response.refunds()).hasSize(1);
    assertThat(response.refunds().getFirst().requestReference()).isNull();
  }

  @Test
  void inspectorViewShowsCommissionAndPayoutButHidesReviewerAndReceivables() {
    Fixture fixture = paidFixture();

    OccurrenceFinanceResponse response =
        OccurrenceFinanceResponse.forInspector(fixture.occurrence());

    assertThat(response.commissionAmount()).isNotNull();
    assertThat(response.tncnAmount()).isNotNull();
    assertThat(response.inspectorNetPayout()).isNotNull();
    assertThat(response.reportAcceptedByUserId()).isNull();
    assertThat(response.paymentConfirmedByUserId()).isNull();
    assertThat(response.payoutRecordedByUserId()).isNull();
    assertThat(response.inspectorReceivables()).isEmpty();
    assertThat(response.payout()).isNotNull();
    assertThat(response.accountingDisclaimer()).contains("MVP assumption");
  }

  private static Fixture paidFixture() {
    ServiceRequestOccurrence occurrence =
        new ServiceRequestOccurrence(
            UUID.randomUUID(),
            1,
            Instant.parse("2027-01-01T08:00:00Z"),
            Instant.parse("2027-01-01T10:00:00Z"));
    ReflectionTestUtils.setField(occurrence, "id", UUID.randomUUID());
    occurrence.snapshotFinance(
        TOTAL, new BigDecimal("10.00"), new BigDecimal("15.00"), new BigDecimal("10.00"));
    UUID inspectorId = UUID.randomUUID();
    occurrence.assign(inspectorId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    occurrence.acceptReport(UUID.randomUUID(), UUID.randomUUID());
    UUID managerId = UUID.randomUUID();
    occurrence.assignPayOSOrder(1001L);
    occurrence.confirmPayment("PAYOS-1", TOTAL, managerId);
    occurrence.recordPayout("PAYOUT-1", managerId);

    ServiceRefund refund =
        occurrence.initiateRefund(
            new BigDecimal("100.00"),
            "REFUND-REQ-1",
            "Service adjustment",
            new BigDecimal("100.00"),
            BigDecimal.ZERO,
            managerId);
    ReflectionTestUtils.setField(refund, "id", UUID.randomUUID());
    InspectorReceivableRepository completionRepository = mock(InspectorReceivableRepository.class);
    when(completionRepository.findByRefundId(refund.getId())).thenReturn(Optional.empty());
    occurrence.completeRefund(
        refund.getId(),
        new BigDecimal("100.00"),
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        "REFUND-TX-1",
        managerId,
        completionRepository);

    InspectorReceivable receivable =
        new InspectorReceivable(
            refund.getId(),
            occurrence,
            inspectorId,
            new BigDecimal("100.00"),
            "REFUND-REQ-1",
            "Inspector payout recovery",
            managerId);
    receivable.recordRecovery(new BigDecimal("40.00"), "RECOVERY-1", "Partial recovery", managerId);

    InspectorReceivableRepository receivables = mock(InspectorReceivableRepository.class);
    when(receivables.findAllByOccurrenceId(occurrence.getId())).thenReturn(List.of(receivable));
    return new Fixture(occurrence, receivables);
  }

  private record Fixture(
      ServiceRequestOccurrence occurrence, InspectorReceivableRepository receivables) {}
}
