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
    Instant paymentConfirmedAt,
    UUID paymentConfirmedByUserId,
    OccurrencePayoutStatus payoutStatus,
    String payoutReference,
    Instant payoutRecordedAt,
    UUID payoutRecordedByUserId,
    String accountingDisclaimer) {

  private static final String MVP_DISCLAIMER =
      "MVP assumption: Tax base = Inspector gross payout after platform commission and before TNCN. "
          + "Configured TNCN rate is applied to this base. Base, rate, and withholding are snapshotted per occurrence. "
          + "This is a simplified product assumption for MVP and accounting validation, not statutory tax advice.";

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
        occurrence.getPaymentConfirmedAt(),
        occurrence.getPaymentConfirmedByUserId(),
        occurrence.getPayoutStatus(),
        occurrence.getPayoutReference(),
        occurrence.getPayoutRecordedAt(),
        occurrence.getPayoutRecordedByUserId(),
        MVP_DISCLAIMER);
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
        occurrence.getPaymentConfirmedAt(),
        null,
        null,
        null,
        null,
        null,
        null);
  }

  /**
   * Scoped financial view for assigned Inspector: shows Inspector gross compensation, TNCN
   * withholding, net payout, and payout status.
   */
  public static OccurrenceFinanceResponse forInspector(ServiceRequestOccurrence occurrence) {
    return new OccurrenceFinanceResponse(
        occurrence.getId(),
        occurrence.getRequestId(),
        occurrence.getOccurrenceIndex(),
        occurrence.getCurrency(),
        null,
        null,
        null,
        null,
        null,
        null,
        occurrence.getInspectorGrossPayout(),
        occurrence.getTncnRate(),
        occurrence.getTncnAmount(),
        occurrence.getInspectorNetPayout(),
        occurrence.getReportAcceptedAt(),
        null,
        occurrence.getAcceptedReportVersionId(),
        occurrence.getPaymentStatus(),
        null,
        null,
        occurrence.getPaymentConfirmedAt(),
        null,
        occurrence.getPayoutStatus(),
        occurrence.getPayoutReference(),
        occurrence.getPayoutRecordedAt(),
        null,
        MVP_DISCLAIMER);
  }
}
