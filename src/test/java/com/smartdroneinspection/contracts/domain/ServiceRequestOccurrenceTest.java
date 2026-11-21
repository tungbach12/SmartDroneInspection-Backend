package com.smartdroneinspection.contracts.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.smartdroneinspection.contracts.repository.InspectorReceivableRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class ServiceRequestOccurrenceTest {

  private static final Instant START = Instant.parse("2027-01-01T08:00:00Z");
  private static final Instant END = Instant.parse("2027-01-01T10:00:00Z");
  private static final BigDecimal TOTAL = new BigDecimal("1000000.00");

  @Test
  void constructorRejectsInvalidOccurrenceDetails() {
    UUID requestId = UUID.randomUUID();
    assertThatThrownBy(() -> new ServiceRequestOccurrence(null, 1, START, END))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ServiceRequestOccurrence(requestId, 0, START, END))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ServiceRequestOccurrence(requestId, 1, null, END))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ServiceRequestOccurrence(requestId, 1, START, null))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new ServiceRequestOccurrence(requestId, 1, END, START))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void assignmentMovesPendingOccurrenceToAssignedAndRecordsInspector() {
    ServiceRequestOccurrence occurrence = occurrence();
    UUID inspectorId = UUID.randomUUID();
    UUID droneId = UUID.randomUUID();
    UUID inspectionId = UUID.randomUUID();
    UUID assignedBy = UUID.randomUUID();

    occurrence.assign(inspectorId, droneId, inspectionId, assignedBy);

    assertThat(occurrence.getStatus())
        .isEqualTo(ServiceRequestOccurrence.OccurrenceStatus.ASSIGNED);
    assertThat(occurrence.getInspectorId()).isEqualTo(inspectorId);
    assertThat(occurrence.getDroneId()).isEqualTo(droneId);
    assertThat(occurrence.getInspectionId()).isEqualTo(inspectionId);
    assertThat(occurrence.getPayout().getInspectorId()).isEqualTo(inspectorId);
    assertThat(occurrence.getAssignedAt()).isNotNull();
  }

  @Test
  void assignmentRejectsIncompleteDetailsAndNonPendingOccurrences() {
    ServiceRequestOccurrence occurrence = occurrence();
    assertThatThrownBy(
            () -> occurrence.assign(null, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("incomplete");

    occurrence.assign(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    assertThatThrownBy(
            () ->
                occurrence.assign(
                    UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("not awaiting assignment");
  }

  @Test
  void rescheduleAndInspectionSyncEnforceLifecycleRules() {
    ServiceRequestOccurrence occurrence = occurrence();
    Instant newStart = START.plusSeconds(3600);
    Instant newEnd = END.plusSeconds(3600);

    occurrence.reschedule(newStart, newEnd);
    assertThat(occurrence.getPlannedStartAt()).isEqualTo(newStart);
    assertThatThrownBy(() -> occurrence.reschedule(newEnd, newStart))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("after start");

    occurrence.syncRescheduleFromInspection(newStart, newEnd);
    assertThat(occurrence.getPlannedStartAt()).isEqualTo(newStart);

    occurrence.assign(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    Instant movedStart = START.plusSeconds(7200);
    Instant movedEnd = END.plusSeconds(7200);
    occurrence.syncRescheduleFromInspection(movedStart, movedEnd);
    assertThat(occurrence.getPlannedStartAt()).isEqualTo(movedStart);
    assertThatThrownBy(() -> occurrence.syncRescheduleFromInspection(movedEnd, movedStart))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void reassignsInspectorOnlyWhilePayoutIsUnpaid() {
    ServiceRequestOccurrence occurrence = assignedOccurrence();
    UUID nextInspector = UUID.randomUUID();
    UUID nextDrone = UUID.randomUUID();

    occurrence.syncReassignFromInspection(nextInspector, nextDrone);

    assertThat(occurrence.getInspectorId()).isEqualTo(nextInspector);
    assertThat(occurrence.getDroneId()).isEqualTo(nextDrone);
    assertThatThrownBy(() -> occurrence.syncReassignFromInspection(null, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Inspector ID is required");
  }

  @Test
  void timeoutRevertReturnsAssignedOccurrenceToPendingAssignment() {
    ServiceRequestOccurrence occurrence = assignedOccurrence();
    UUID timedOutInspector = occurrence.getInspectorId();
    Instant timedOutAt = Instant.now();

    assertThat(occurrence.revertTimeoutToPendingAssignment(timedOutInspector, timedOutAt)).isTrue();

    assertThat(occurrence.getStatus())
        .isEqualTo(ServiceRequestOccurrence.OccurrenceStatus.PENDING_ASSIGNMENT);
    assertThat(occurrence.getInspectorId()).isNull();
    assertThat(occurrence.getDroneId()).isNull();
    assertThat(occurrence.getInspectionId()).isNull();
    assertThat(occurrence.getLastTimeoutCancelledAt()).isEqualTo(timedOutAt);
    assertThat(occurrence.getLastTimeoutInspectorId()).isEqualTo(timedOutInspector);
    assertThat(occurrence.getPayout().getInspectorId()).isNull();
  }

  @Test
  void timeoutRevertSkipsPaidOrUnassignedOccurrences() {
    ServiceRequestOccurrence pending = occurrence();
    assertThat(pending.revertTimeoutToPendingAssignment(UUID.randomUUID(), Instant.now()))
        .isFalse();

    ServiceRequestOccurrence paid = assignedOccurrence();
    paid.acceptReport();
    paid.confirmPayment("PAID-1", TOTAL, UUID.randomUUID());
    assertThat(paid.revertTimeoutToPendingAssignment(UUID.randomUUID(), Instant.now())).isFalse();
  }

  @Test
  void cancellationIsTerminalAndBlockedAfterSettlement() {
    ServiceRequestOccurrence pending = occurrence();
    pending.syncCancelFromInspection();
    assertThat(pending.getStatus()).isEqualTo(ServiceRequestOccurrence.OccurrenceStatus.CANCELLED);
    pending.syncCancelFromInspection();

    ServiceRequestOccurrence paid = assignedOccurrence();
    paid.acceptReport();
    paid.confirmPayment("PAID-1", TOTAL, UUID.randomUUID());
    assertThatThrownBy(paid::syncCancelFromInspection)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("report was accepted or payment was settled");
  }

  @Test
  void reportAcceptanceStartsPaymentDueAndRequiresVersionDetails() {
    ServiceRequestOccurrence occurrence = occurrence();
    assertThatThrownBy(() -> occurrence.acceptReport(null, UUID.randomUUID()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> occurrence.acceptReport(UUID.randomUUID(), null))
        .isInstanceOf(IllegalArgumentException.class);

    occurrence.acceptReport(UUID.randomUUID(), UUID.randomUUID());

    assertThat(occurrence.getReportAcceptedAt()).isNotNull();
    assertThat(occurrence.getPaymentStatus())
        .isEqualTo(ServiceRequestOccurrence.OccurrencePaymentStatus.PAYMENT_DUE);
    assertThatCode(() -> occurrence.startPaymentDueWindow(Duration.ofDays(15)))
        .doesNotThrowAnyException();
  }

  @Test
  void paymentDeadlineRequiresAcceptedReport() {
    ServiceRequestOccurrence occurrence = occurrence();

    assertThatThrownBy(() -> occurrence.startPaymentDueWindow(Duration.ofDays(15)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Report must be accepted");
  }

  @Test
  void paymentConfirmationValidatesReferenceAmountAndAcceptance() {
    ServiceRequestOccurrence occurrence = occurrence();
    assertThatThrownBy(() -> occurrence.confirmPayment(null, TOTAL, UUID.randomUUID()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("reference");
    assertThatThrownBy(() -> occurrence.confirmPayment("REF", null, UUID.randomUUID()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("amount");
    assertThatThrownBy(() -> occurrence.confirmPayment("REF", TOTAL, UUID.randomUUID()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("accept the report");

    occurrence.acceptReport();
    assertThatThrownBy(
            () -> occurrence.confirmPayment("REF", new BigDecimal("999999.00"), UUID.randomUUID()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must match agreed total");

    occurrence.confirmPayment(" REF ", TOTAL, UUID.randomUUID());
    assertThat(occurrence.getPaymentStatus())
        .isEqualTo(ServiceRequestOccurrence.OccurrencePaymentStatus.PAID);
    assertThat(occurrence.getPaymentReference()).isEqualTo("REF");
    assertThat(occurrence.getPaymentAmount()).isEqualByComparingTo(TOTAL);
    assertThat(occurrence.getPayoutStatus())
        .isEqualTo(ServiceRequestOccurrence.OccurrencePayoutStatus.ELIGIBLE);
  }

  @Test
  void onlinePaymentConfirmationRecordsGatewayMethod() {
    ServiceRequestOccurrence occurrence = occurrence();
    occurrence.acceptReport();
    occurrence.assignSepayOrder(12345L);

    occurrence.confirmOnlinePayment("SEPAY-1", TOTAL, "SEPAY");

    assertThat(occurrence.getPaymentMethod()).isEqualTo("SEPAY");
    assertThat(occurrence.getGatewayOrderCode()).isEqualTo(12345L);
    assertThat(occurrence.getPaymentStatus())
        .isEqualTo(ServiceRequestOccurrence.OccurrencePaymentStatus.PAID);
  }

  @Test
  void gatewayOrderLifecycleRejectsConflicts() {
    ServiceRequestOccurrence occurrence = occurrence();
    assertThatThrownBy(() -> occurrence.assignSepayOrder(1L))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("not due");

    occurrence.acceptReport();
    occurrence.assignSepayOrder(42L);
    occurrence.assignSepayOrder(42L);
    assertThat(occurrence.hasActivePaymentOrder("SEPAY")).isTrue();
    assertThat(occurrence.hasActivePaymentOrder("VNPAY")).isFalse();
    assertThatThrownBy(() -> occurrence.assignSepayOrder(43L))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("active payment order");
    assertThatThrownBy(() -> occurrence.assignVnPayOrder(44L))
        .isInstanceOf(IllegalStateException.class);
    assertThatThrownBy(() -> occurrence.clearUnpaidPaymentOrder(43L, "SEPAY"))
        .isInstanceOf(IllegalStateException.class);
    occurrence.clearUnpaidPaymentOrder(42L, "SEPAY");
    assertThat(occurrence.getGatewayOrderCode()).isNull();
    occurrence.assignVnPayOrder(44L);
    assertThat(occurrence.hasActivePaymentOrder("VNPAY")).isTrue();
  }

  @Test
  void payoutRequiresAcceptedPaymentAndInspector() {
    ServiceRequestOccurrence occurrence = assignedOccurrence();
    assertThatThrownBy(() -> occurrence.recordPayout("PAYOUT-1", UUID.randomUUID()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("accepted report and confirmed payment");

    occurrence.acceptReport();
    occurrence.confirmPayment("PAID-1", TOTAL, UUID.randomUUID());
    assertThatThrownBy(() -> occurrence.recordPayout(" ", UUID.randomUUID()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("reference");
    occurrence.recordPayout("PAYOUT-1", UUID.randomUUID());
    assertThat(occurrence.getPayoutStatus())
        .isEqualTo(ServiceRequestOccurrence.OccurrencePayoutStatus.PAID);
    occurrence.recordPayout("PAYOUT-1", UUID.randomUUID());
    assertThatThrownBy(() -> occurrence.recordPayout("PAYOUT-2", UUID.randomUUID()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("already recorded");
  }

  @Test
  void initiateRefundRequiresPaidOccurrenceAndConsistentRecoveryPlan() {
    ServiceRequestOccurrence occurrence = occurrence();
    assertThatThrownBy(
            () ->
                occurrence.initiateRefund(
                    new BigDecimal("100.00"),
                    "REQ-1",
                    "reason",
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    UUID.randomUUID()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Only a paid occurrence");

    paidOccurrence(occurrence);
    assertThatThrownBy(
            () ->
                occurrence.initiateRefund(
                    BigDecimal.ZERO, "REQ-1", "reason", BigDecimal.ZERO, BigDecimal.ZERO, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("positive");
    assertThatThrownBy(
            () ->
                occurrence.initiateRefund(
                    new BigDecimal("100.00"),
                    "  ",
                    "reason",
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    UUID.randomUUID()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("reference and reason");
  }

  @Test
  void prePayoutRefundHoldsAndReleasesInspectorPayout() {
    ServiceRequestOccurrence occurrence = occurrence();
    paidOccurrence(occurrence);
    UUID managerId = UUID.randomUUID();

    ServiceRefund refund =
        occurrence.initiateRefund(
            new BigDecimal("100.00"),
            "REQ-1",
            "Service adjustment",
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            managerId);

    ReflectionTestUtils.setField(refund, "id", UUID.randomUUID());
    assertThat(occurrence.isPayoutHeldForRefund()).isTrue();
    assertThat(refund.getStatus()).isEqualTo(ServiceRefund.RefundStatus.INITIATED);
    assertThatThrownBy(
            () ->
                occurrence.initiateRefund(
                    new BigDecimal("10.00"),
                    "REQ-2",
                    "Another",
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    managerId))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("existing refund");

    occurrence.completeRefund(
        refund.getId(),
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        new BigDecimal("100.00"),
        "TX-1",
        managerId,
        mock(InspectorReceivableRepository.class));

    assertThat(refund.getStatus()).isEqualTo(ServiceRefund.RefundStatus.COMPLETED);
    assertThat(occurrence.getRefundedAmount()).isEqualByComparingTo("100.00");
    assertThat(occurrence.getRefundStatus()).isEqualTo("PARTIAL");
    assertThat(occurrence.isPayoutHeldForRefund()).isFalse();
  }

  @Test
  void cancellingRefundReleasesThePayoutHold() {
    ServiceRequestOccurrence occurrence = occurrence();
    paidOccurrence(occurrence);
    ServiceRefund refund =
        occurrence.initiateRefund(
            new BigDecimal("100.00"),
            "REQ-1",
            "Service adjustment",
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            UUID.randomUUID());

    ReflectionTestUtils.setField(refund, "id", UUID.randomUUID());
    occurrence.cancelRefund(refund.getId(), "Customer withdrew", UUID.randomUUID());

    assertThat(refund.getStatus()).isEqualTo(ServiceRefund.RefundStatus.CANCELLED);
    assertThat(occurrence.isPayoutHeldForRefund()).isFalse();
    assertThatThrownBy(
            () -> occurrence.cancelRefund(UUID.randomUUID(), "missing", UUID.randomUUID()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Refund not found");
  }

  @Test
  void postPayoutRefundCreatesInspectorReceivable() {
    ServiceRequestOccurrence occurrence = assignedOccurrence();
    ReflectionTestUtils.setField(occurrence, "id", UUID.randomUUID());
    paidOccurrence(occurrence);
    occurrence.recordPayout("PAYOUT-1", UUID.randomUUID());
    UUID managerId = UUID.randomUUID();
    ServiceRefund refund =
        occurrence.initiateRefund(
            new BigDecimal("100.00"),
            "REQ-1",
            "Service adjustment",
            new BigDecimal("100.00"),
            BigDecimal.ZERO,
            managerId);
    InspectorReceivableRepository receivables = mock(InspectorReceivableRepository.class);
    ReflectionTestUtils.setField(refund, "id", UUID.randomUUID());
    when(receivables.findByRefundId(refund.getId())).thenReturn(Optional.empty());

    occurrence.completeRefund(
        refund.getId(),
        new BigDecimal("100.00"),
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        "TX-1",
        managerId,
        receivables);

    assertThat(occurrence.getPayoutStatus())
        .isEqualTo(ServiceRequestOccurrence.OccurrencePayoutStatus.PAID);
    verify(receivables).save(any(InspectorReceivable.class));
  }

  @Test
  void postPayoutRefundRequiresMatchingRecoveryPlan() {
    ServiceRequestOccurrence occurrence = assignedOccurrence();
    paidOccurrence(occurrence);
    occurrence.recordPayout("PAYOUT-1", UUID.randomUUID());
    UUID managerId = UUID.randomUUID();

    assertThatThrownBy(
            () ->
                occurrence.initiateRefund(
                    new BigDecimal("100.00"),
                    "REQ-1",
                    "Service adjustment",
                    new BigDecimal("80.00"),
                    new BigDecimal("10.00"),
                    managerId))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Post-payout recovery");
  }

  @Test
  void refundCompletionValidatesAllocationTotalAndPayoutRemaining() {
    ServiceRequestOccurrence occurrence = occurrence();
    paidOccurrence(occurrence);
    ServiceRefund refund =
        occurrence.initiateRefund(
            new BigDecimal("100.00"),
            "REQ-1",
            "Service adjustment",
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            UUID.randomUUID());
    InspectorReceivableRepository receivables = mock(InspectorReceivableRepository.class);
    ReflectionTestUtils.setField(refund, "id", UUID.randomUUID());

    assertThatThrownBy(
            () ->
                occurrence.completeRefund(
                    UUID.randomUUID(),
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    new BigDecimal("100.00"),
                    "TX-1",
                    UUID.randomUUID(),
                    receivables))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Refund not found");

    assertThatThrownBy(
            () ->
                occurrence.completeRefund(
                    refund.getId(),
                    new BigDecimal("100.00"),
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    new BigDecimal("100.00"),
                    "TX-1",
                    UUID.randomUUID(),
                    receivables))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("exactly equal");

    assertThatThrownBy(
            () ->
                occurrence.completeRefund(
                    refund.getId(),
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    new BigDecimal("99.00"),
                    "TX-1",
                    UUID.randomUUID(),
                    receivables))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("exactly equal");
    verify(receivables, never()).save(any(InspectorReceivable.class));
  }

  @Test
  void receivableRecoveryRequiresOwnership() {
    ServiceRequestOccurrence occurrence = occurrence();
    ReflectionTestUtils.setField(occurrence, "id", UUID.randomUUID());
    InspectorReceivableRepository receivables = mock(InspectorReceivableRepository.class);
    UUID receivableId = UUID.randomUUID();
    when(receivables.findByIdForUpdate(receivableId)).thenReturn(Optional.empty());

    assertThatThrownBy(
            () ->
                occurrence.recordReceivableRecovery(
                    receivableId,
                    new BigDecimal("10.00"),
                    "TX",
                    "note",
                    UUID.randomUUID(),
                    receivables))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("not found");

    InspectorReceivable receivable = mock(InspectorReceivable.class);
    when(receivable.getOccurrenceId()).thenReturn(UUID.randomUUID());
    when(receivables.findByIdForUpdate(receivableId)).thenReturn(Optional.of(receivable));
    assertThatThrownBy(
            () ->
                occurrence.recordReceivableRecovery(
                    receivableId,
                    new BigDecimal("10.00"),
                    "TX",
                    "note",
                    UUID.randomUUID(),
                    receivables))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("another occurrence");
  }

  @Test
  void legacyRefundAllocationCanBeReconciled() {
    ServiceRequestOccurrence occurrence = assignedOccurrence();
    ReflectionTestUtils.setField(occurrence, "id", UUID.randomUUID());
    paidOccurrence(occurrence);
    occurrence.recordPayout("PAYOUT-1", UUID.randomUUID());
    ServiceRefund refund =
        occurrence.initiateRefund(
            new BigDecimal("100.00"),
            "REQ-1",
            "Service adjustment",
            new BigDecimal("100.00"),
            BigDecimal.ZERO,
            UUID.randomUUID());
    ReflectionTestUtils.setField(refund, "status", ServiceRefund.RefundStatus.COMPLETED);
    ReflectionTestUtils.setField(refund, "id", UUID.randomUUID());
    InspectorReceivableRepository receivables = mock(InspectorReceivableRepository.class);
    when(receivables.findByRefundId(refund.getId())).thenReturn(Optional.empty());

    occurrence.reconcileRefundAllocation(
        refund.getId(),
        new BigDecimal("100.00"),
        BigDecimal.ZERO,
        new BigDecimal("100.00"),
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        UUID.randomUUID(),
        "TX-1",
        "reconciled",
        receivables);

    assertThat(refund.isAllocationMissing()).isFalse();
    verify(receivables).save(any(InspectorReceivable.class));
  }

  private static ServiceRequestOccurrence occurrence() {
    ServiceRequestOccurrence occurrence =
        new ServiceRequestOccurrence(UUID.randomUUID(), 1, START, END);
    occurrence.snapshotFinance(
        TOTAL, new BigDecimal("10.00"), new BigDecimal("15.00"), new BigDecimal("10.00"));
    return occurrence;
  }

  private static ServiceRequestOccurrence assignedOccurrence() {
    ServiceRequestOccurrence occurrence = occurrence();
    occurrence.assign(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
    return occurrence;
  }

  private static void paidOccurrence(ServiceRequestOccurrence occurrence) {
    occurrence.acceptReport();
    occurrence.confirmPayment("PAID-1", TOTAL, UUID.randomUUID());
  }
}
