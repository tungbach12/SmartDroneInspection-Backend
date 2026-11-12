package com.smartdroneinspection.contracts.api.dto.response;

import com.smartdroneinspection.contracts.domain.ServiceRequestOccurrence;
import com.smartdroneinspection.contracts.domain.ServiceRequestOccurrence.OccurrencePaymentStatus;
import com.smartdroneinspection.contracts.domain.ServiceRequestOccurrence.OccurrencePayoutStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record OccurrenceFinanceResponse(
    UUID occurrenceId,
    UUID requestId,
    int occurrenceIndex,
    String currency,
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
    Instant reportAcceptedAt,
    UUID reportAcceptedByUserId,
    UUID acceptedReportVersionId,
    OccurrencePaymentStatus paymentStatus,
    String paymentReference,
    BigDecimal paymentAmount,
    BigDecimal refundedAmount,
    String refundStatus,
    java.util.List<RefundSummary> refunds,
    java.util.List<InspectorReceivableSummary> inspectorReceivables,
    Instant paymentConfirmedAt,
    UUID paymentConfirmedByUserId,
    OccurrencePayoutStatus payoutStatus,
    String payoutReference,
    Instant payoutRecordedAt,
    UUID payoutRecordedByUserId,
    String accountingDisclaimer,
    ServiceInvoiceSummary invoice,
    ServicePaymentSummary payment,
    InspectorPayoutSummary payout) {

  public record ServiceInvoiceSummary(
      BigDecimal totalPrice,
      BigDecimal vatRate,
      BigDecimal commissionRate,
      BigDecimal tncnRate,
      Instant reportAcceptedAt) {}

  public record ServicePaymentSummary(
      OccurrencePaymentStatus status,
      Long payosOrderCode,
      String method,
      String reference,
      Instant confirmedAt) {}

  public record InspectorReceivableSummary(
      UUID id,
      UUID refundId,
      BigDecimal assessedAmount,
      BigDecimal recoveredAmount,
      BigDecimal outstandingAmount,
      String sourceReference,
      String reason,
      java.util.List<InspectorReceivableRecoverySummary> recoveries) {}

  public record InspectorReceivableRecoverySummary(
      UUID id,
      BigDecimal amount,
      String externalReference,
      String note,
      UUID recordedByUserId,
      Instant recordedAt) {}

  public record InspectorPayoutSummary(
      UUID inspectorId,
      OccurrencePayoutStatus status,
      String reference,
      Instant recordedAt,
      UUID recordedByUserId,
      boolean heldForRefund,
      BigDecimal finalPayoutAmount) {}

  public record RefundSummary(
      java.util.UUID id,
      BigDecimal amount,
      String status,
      String requestReference,
      BigDecimal inspectorReceivableAmount,
      BigDecimal platformAbsorbedPlanAmount,
      UUID initiatedByUserId,
      Instant initiatedAt,
      UUID completedByUserId,
      Instant completedAt,
      UUID cancelledByUserId,
      Instant cancelledAt,
      String cancellationReason,
      BigDecimal inspectorPayoutReduction,
      BigDecimal platformCommissionAdjustment,
      BigDecimal vatAdjustment,
      BigDecimal tncnAdjustment,
      BigDecimal platformAbsorbedAmount,
      String reference,
      String reason,
      UUID recordedByUserId,
      UUID allocationReconciledByUserId,
      String allocationReconciliationReference,
      String allocationReconciliationNote,
      Instant allocationReconciledAt,
      Instant recordedAt) {}

  private static final String MVP_DISCLAIMER =
      "MVP assumption: Tax base = Inspector gross payout after platform commission and before TNCN. "
          + "Configured TNCN rate is applied to this base. Base, rate, and withholding are snapshotted per occurrence. "
          + "This is a simplified product assumption for MVP and accounting validation, not statutory tax advice.";

  private static ServiceInvoiceSummary invoiceSummary(ServiceRequestOccurrence occurrence) {
    return new ServiceInvoiceSummary(
        occurrence.getTotalPrice(),
        occurrence.getVatRate(),
        occurrence.getCommissionRate(),
        occurrence.getTncnRate(),
        occurrence.getReportAcceptedAt());
  }

  private static java.util.List<InspectorReceivableSummary> receivableSummaries(
      ServiceRequestOccurrence occurrence,
      com.smartdroneinspection.contracts.repository.InspectorReceivableRepository receivables) {
    return receivables.findAllByOccurrenceId(occurrence.getId()).stream()
        .map(
            receivable ->
                new InspectorReceivableSummary(
                    receivable.getId(),
                    receivable.getRefundId(),
                    receivable.getAssessedAmount(),
                    receivable.getRecoveredAmount(),
                    receivable.getOutstandingAmount(),
                    receivable.getSourceReference(),
                    receivable.getReason(),
                    receivable.getRecoveries().stream()
                        .map(
                            recovery ->
                                new InspectorReceivableRecoverySummary(
                                    recovery.getId(),
                                    recovery.getAmount(),
                                    recovery.getExternalReference(),
                                    recovery.getNote(),
                                    recovery.getRecordedByUserId(),
                                    recovery.getRecordedAt()))
                        .toList()))
        .toList();
  }

  private static java.util.List<RefundSummary> refundSummaries(
      ServiceRequestOccurrence occurrence) {
    return occurrence.getRefunds().stream()
        .map(
            refund ->
                new RefundSummary(
                    refund.getId(),
                    refund.getAmount(),
                    refund.getStatus().name(),
                    refund.getRequestReference(),
                    refund.getInspectorReceivableAmount(),
                    refund.getPlatformAbsorbedPlanAmount(),
                    refund.getInitiatedByUserId(),
                    refund.getInitiatedAt(),
                    refund.getCompletedByUserId(),
                    refund.getCompletedAt(),
                    refund.getCancelledByUserId(),
                    refund.getCancelledAt(),
                    refund.getCancellationReason(),
                    refund.getInspectorPayoutReduction(),
                    refund.getPlatformCommissionAdjustment(),
                    refund.getVatAdjustment(),
                    refund.getTncnAdjustment(),
                    refund.getPlatformAbsorbedAmount(),
                    refund.getReference(),
                    refund.getReason(),
                    refund.getRecordedByUserId(),
                    refund.getAllocationReconciledByUserId(),
                    refund.getAllocationReconciliationReference(),
                    refund.getAllocationReconciliationNote(),
                    refund.getAllocationReconciledAt(),
                    refund.getRecordedAt()))
        .toList();
  }

  private static java.util.List<RefundSummary> customerRefundSummaries(
      ServiceRequestOccurrence occurrence) {
    return occurrence.getRefunds().stream()
        .map(
            refund ->
                new RefundSummary(
                    refund.getId(),
                    refund.getAmount(),
                    refund.getStatus().name(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    refund.getRecordedAt()))
        .toList();
  }

  private static ServicePaymentSummary paymentSummary(ServiceRequestOccurrence occurrence) {
    return new ServicePaymentSummary(
        occurrence.getPaymentStatus(),
        occurrence.getPayosOrderCode(),
        occurrence.getPaymentMethod(),
        occurrence.getPaymentReference(),
        occurrence.getPaymentConfirmedAt());
  }

  private static InspectorPayoutSummary payoutSummary(ServiceRequestOccurrence occurrence) {
    return new InspectorPayoutSummary(
        occurrence.getPayout().getInspectorId(),
        occurrence.getPayoutStatus(),
        occurrence.getPayoutReference(),
        occurrence.getPayoutRecordedAt(),
        occurrence.getPayout().getPayoutRecordedByUserId(),
        occurrence.isPayoutHeldForRefund(),
        occurrence.getPayout().getFinalPayoutAmount());
  }

  /** Full financial view for Managers and Admins. */
  public static OccurrenceFinanceResponse forManager(
      ServiceRequestOccurrence occurrence,
      com.smartdroneinspection.contracts.repository.InspectorReceivableRepository receivables) {
    return new OccurrenceFinanceResponse(
        occurrence.getId(),
        occurrence.getRequestId(),
        occurrence.getOccurrenceIndex(),
        occurrence.getCurrency(),
        occurrence.getTotalPrice(),
        occurrence.getServiceBaseAmount(),
        occurrence.getVatRate(),
        occurrence.getVatAmount(),
        occurrence.getCommissionRate(),
        occurrence.getCommissionAmount(),
        occurrence.getInspectorGrossPayout(),
        occurrence.getTncnRate(),
        occurrence.getTncnAmount(),
        occurrence.getInspectorNetPayout(),
        occurrence.getReportAcceptedAt(),
        occurrence.getReportAcceptedByUserId(),
        occurrence.getAcceptedReportVersionId(),
        occurrence.getPaymentStatus(),
        occurrence.getPaymentReference(),
        occurrence.getPaymentAmount(),
        occurrence.getRefundedAmount(),
        occurrence.getRefundStatus(),
        refundSummaries(occurrence),
        receivableSummaries(occurrence, receivables),
        occurrence.getPaymentConfirmedAt(),
        occurrence.getPaymentConfirmedByUserId(),
        occurrence.getPayoutStatus(),
        occurrence.getPayoutReference(),
        occurrence.getPayoutRecordedAt(),
        occurrence.getPayoutRecordedByUserId(),
        MVP_DISCLAIMER,
        invoiceSummary(occurrence),
        paymentSummary(occurrence),
        payoutSummary(occurrence));
  }

  /**
   * Scoped financial view for Customers: shows only inclusive agreed total, pre-VAT base, VAT and
   * payment status. Internal platform commission, inspector tax base, and payout amounts are
   * strictly masked.
   */
  public static OccurrenceFinanceResponse forCustomer(ServiceRequestOccurrence occurrence) {
    return new OccurrenceFinanceResponse(
        occurrence.getId(),
        occurrence.getRequestId(),
        occurrence.getOccurrenceIndex(),
        occurrence.getCurrency(),
        occurrence.getTotalPrice(),
        occurrence.getServiceBaseAmount(),
        occurrence.getVatRate(),
        occurrence.getVatAmount(),
        null,
        null,
        null,
        null,
        null,
        null,
        occurrence.getReportAcceptedAt(),
        occurrence.getReportAcceptedByUserId(),
        occurrence.getAcceptedReportVersionId(),
        occurrence.getPaymentStatus(),
        null,
        occurrence.getPaymentAmount(),
        occurrence.getRefundedAmount(),
        occurrence.getRefundStatus(),
        customerRefundSummaries(occurrence),
        java.util.List.of(),
        occurrence.getPaymentConfirmedAt(),
        null,
        null,
        null,
        null,
        null,
        null,
        new ServiceInvoiceSummary(
            occurrence.getTotalPrice(),
            occurrence.getVatRate(),
            null,
            null,
            occurrence.getReportAcceptedAt()),
        new ServicePaymentSummary(
            occurrence.getPaymentStatus(),
            occurrence.getPayosOrderCode(),
            occurrence.getPaymentMethod(),
            null,
            occurrence.getPaymentConfirmedAt()),
        null);
  }

  /**
   * Scoped financial view for assigned Inspector: shows full order price, platform commission,
   * Inspector gross compensation, TNCN withholding, net payout, and attributable payout status.
   */
  public static OccurrenceFinanceResponse forInspector(ServiceRequestOccurrence occurrence) {
    return new OccurrenceFinanceResponse(
        occurrence.getId(),
        occurrence.getRequestId(),
        occurrence.getOccurrenceIndex(),
        occurrence.getCurrency(),
        occurrence.getTotalPrice(),
        occurrence.getServiceBaseAmount(),
        occurrence.getVatRate(),
        occurrence.getVatAmount(),
        occurrence.getCommissionRate(),
        occurrence.getCommissionAmount(),
        occurrence.getInspectorGrossPayout(),
        occurrence.getTncnRate(),
        occurrence.getTncnAmount(),
        occurrence.getInspectorNetPayout(),
        occurrence.getReportAcceptedAt(),
        null,
        occurrence.getAcceptedReportVersionId(),
        occurrence.getPaymentStatus(),
        null,
        occurrence.getPaymentAmount(),
        occurrence.getRefundedAmount(),
        occurrence.getRefundStatus(),
        refundSummaries(occurrence),
        java.util.List.of(),
        occurrence.getPaymentConfirmedAt(),
        null,
        occurrence.getPayoutStatus(),
        occurrence.getPayoutReference(),
        occurrence.getPayoutRecordedAt(),
        null,
        MVP_DISCLAIMER,
        invoiceSummary(occurrence),
        new ServicePaymentSummary(
            occurrence.getPaymentStatus(),
            occurrence.getPayosOrderCode(),
            occurrence.getPaymentMethod(),
            null,
            occurrence.getPaymentConfirmedAt()),
        payoutSummary(occurrence));
  }
}
