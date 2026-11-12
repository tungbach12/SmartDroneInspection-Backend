package com.smartdroneinspection.contracts.api.dto.response;

import com.smartdroneinspection.contracts.domain.ServiceRequest;
import com.smartdroneinspection.contracts.domain.ServiceRequestOccurrence;
import com.smartdroneinspection.contracts.domain.ServiceRequestOccurrence.OccurrencePaymentStatus;
import com.smartdroneinspection.contracts.domain.ServiceRequestOccurrence.OccurrencePayoutStatus;
import com.smartdroneinspection.contracts.domain.ServiceRequestOccurrence.OccurrenceStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ServiceRequestOccurrenceResponse(
    UUID id,
    int occurrenceIndex,
    Instant plannedStartAt,
    Instant plannedEndAt,
    OccurrenceStatus status,
    UUID inspectorId,
    UUID droneId,
    UUID inspectionId,
    Instant assignedAt,
    String contractFileName,
    String contractContentType,
    Long contractSizeBytes,
    Instant contractUploadedAt,
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
    BigDecimal remainingInspectorNetPayout,
    OccurrencePaymentStatus paymentStatus,
    Instant paymentConfirmedAt,
    java.math.BigDecimal refundedAmount,
    String refundStatus,
    OccurrencePayoutStatus payoutStatus,
    Instant payoutRecordedAt,
    Instant reportAcceptedAt,
    OccurrenceFinanceResponse.ServiceInvoiceSummary invoice,
    OccurrenceFinanceResponse.ServicePaymentSummary payment,
    OccurrenceFinanceResponse.InspectorPayoutSummary payout) {

  public static ServiceRequestOccurrenceResponse from(ServiceRequestOccurrence occurrence) {
    return from(occurrence, null);
  }

  public static ServiceRequestOccurrenceResponse from(
      ServiceRequestOccurrence occurrence, ServiceRequest request) {
    return new ServiceRequestOccurrenceResponse(
        occurrence.getId(),
        occurrence.getOccurrenceIndex(),
        occurrence.getPlannedStartAt(),
        occurrence.getPlannedEndAt(),
        occurrence.getStatus(),
        occurrence.getInspectorId(),
        occurrence.getDroneId(),
        occurrence.getInspectionId(),
        occurrence.getAssignedAt(),
        request != null ? request.getContractFileName() : null,
        request != null ? request.getContractContentType() : null,
        request != null ? request.getContractSizeBytes() : null,
        request != null ? request.getContractUploadedAt() : null,
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
        occurrence.getRemainingInspectorNetPayout(),
        occurrence.getPaymentStatus(),
        occurrence.getPaymentConfirmedAt(),
        occurrence.getRefundedAmount(),
        occurrence.getRefundStatus(),
        occurrence.getPayoutStatus(),
        occurrence.getPayoutRecordedAt(),
        occurrence.getReportAcceptedAt(),
        new OccurrenceFinanceResponse.ServiceInvoiceSummary(
            occurrence.getTotalPrice(),
            occurrence.getVatRate(),
            occurrence.getCommissionRate(),
            occurrence.getTncnRate(),
            occurrence.getReportAcceptedAt()),
        new OccurrenceFinanceResponse.ServicePaymentSummary(
            occurrence.getPaymentStatus(),
            occurrence.getPayosOrderCode(),
            occurrence.getPaymentMethod(),
            occurrence.getPaymentReference(),
            occurrence.getPaymentConfirmedAt()),
        new OccurrenceFinanceResponse.InspectorPayoutSummary(
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
            occurrence.getPayout().getFinalPayoutAmount()));
  }
}
