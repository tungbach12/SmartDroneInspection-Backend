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

  public record InspectorPayoutSummary(
      UUID inspectorId,
      OccurrencePayoutStatus status,
      String reference,
      Instant recordedAt,
      UUID recordedByUserId,
      boolean heldForRefund,
      String refundReconciliationReference,
      String refundReconciliationNote,
      UUID refundReconciledByUserId,
      Instant refundReconciledAt,
      BigDecimal finalPayoutAmount) {}

  public record RefundSummary(
      java.util.UUID id,
      BigDecimal amount,
      String reference,
      String reason,
      UUID recordedByUserId,
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

  private static java.util.List<RefundSummary> refundSummaries(
      ServiceRequestOccurrence occurrence) {
    return occurrence.getRefunds().stream()
        .map(
            refund ->
                new RefundSummary(
                    refund.getId(),
                    refund.getAmount(),
                    refund.getReference(),
                    refund.getReason(),
                    refund.getRecordedByUserId(),
                    refund.getRecordedAt()))
        .toList();
  }

  private static java.util.List<RefundSummary> customerRefundSummaries(
      ServiceRequestOccurrence occurrence) {
    return occurrence.getRefunds().stream()
        .map(
            refund ->
                new RefundSummary(
                    refund.getId(), refund.getAmount(), null, null, null, refund.getRecordedAt()))
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
        occurrence.getPayout().getRefundReconciliationReference(),
        occurrence.getPayout().getRefundReconciliationNote(),
        occurrence.getPayout().getRefundReconciledByUserId(),
        occurrence.getPayout().getRefundReconciledAt(),
        occurrence.getPayout().getFinalPayoutAmount());
  }

  /** Full financial view for Managers and Admins. */
  public static OccurrenceFinanceResponse forManager(ServiceRequestOccurrence occurrence) {
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
