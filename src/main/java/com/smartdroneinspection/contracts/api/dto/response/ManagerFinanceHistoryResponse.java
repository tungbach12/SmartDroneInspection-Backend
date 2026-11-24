package com.smartdroneinspection.contracts.api.dto.response;

import com.smartdroneinspection.contracts.domain.ServiceRefund;
import com.smartdroneinspection.contracts.domain.ServiceRequestOccurrence;
import com.smartdroneinspection.contracts.domain.ServiceRequestOccurrence.OccurrencePayoutStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ManagerFinanceHistoryResponse(
    ManagerFinanceSummary summary,
    List<ManagerFinanceHistoryItem> content,
    long totalElements,
    int page,
    int size) {

  public ManagerFinanceHistoryResponse {
    content = content == null ? List.of() : List.copyOf(content);
  }

  public record ManagerFinanceSummary(
      String currency,
      long transactionCount,
      BigDecimal totalCustomerPaid,
      BigDecimal totalRefunded,
      BigDecimal netPlatformInflow,
      BigDecimal totalCommissionEarned,
      BigDecimal totalVatAmount,
      BigDecimal totalTncnWithheld,
      BigDecimal totalInspectorNetPayout,
      BigDecimal paidInspectorPayout,
      BigDecimal pendingInspectorPayout) {}

  public record ManagerFinanceHistoryItem(
      UUID occurrenceId,
      UUID requestId,
      int occurrenceIndex,
      String requestDescription,
      UUID assetId,
      String assetName,
      UUID customerId,
      String customerName,
      UUID inspectorId,
      String inspectorName,
      String currency,
      BigDecimal customerPaidAmount,
      BigDecimal serviceBaseAmount,
      BigDecimal vatRate,
      BigDecimal vatAmount,
      BigDecimal commissionRate,
      BigDecimal commissionAmount,
      BigDecimal netCommissionAmount,
      BigDecimal tncnRate,
      BigDecimal tncnAmount,
      BigDecimal inspectorNetPayout,
      BigDecimal refundedAmount,
      BigDecimal netPlatformInflow,
      String paymentStatus,
      String paymentMethod,
      String paymentReference,
      Instant paymentConfirmedAt,
      String payoutStatus,
      String payoutReference,
      Instant payoutRecordedAt,
      Instant payoutUpdatedAt) {

    public static ManagerFinanceHistoryItem from(
        ServiceRequestOccurrence occurrence,
        String requestDescription,
        UUID assetId,
        String assetName,
        UUID customerId,
        String customerName,
        UUID inspectorId,
        String inspectorName) {
      BigDecimal customerPaid = nonNullZero(occurrence.getPaymentAmount());
      BigDecimal refunded = nonNullZero(occurrence.getRefundedAmount());
      BigDecimal netInflow = customerPaid.subtract(refunded).max(BigDecimal.ZERO);
      BigDecimal grossCommission = nonNullZero(occurrence.getCommissionAmount());
      BigDecimal commissionReduction = completedCommissionReduction(occurrence);
      BigDecimal netCommission = grossCommission.subtract(commissionReduction).max(BigDecimal.ZERO);
      BigDecimal inspectorNet = resolveInspectorNetPayout(occurrence);
      Instant payoutUpdatedAt =
          occurrence.getPayout() != null
              ? occurrence.getPayout().getUpdatedAt()
              : occurrence.getUpdatedAt();

      return new ManagerFinanceHistoryItem(
          occurrence.getId(),
          occurrence.getRequestId(),
          occurrence.getOccurrenceIndex(),
          requestDescription,
          assetId,
          assetName,
          customerId,
          customerName,
          inspectorId,
          inspectorName,
          occurrence.getCurrency(),
          customerPaid,
          occurrence.getServiceBaseAmount(),
          occurrence.getVatRate(),
          nonNullZero(occurrence.getVatAmount()),
          occurrence.getCommissionRate(),
          grossCommission,
          netCommission,
          occurrence.getTncnRate(),
          nonNullZero(occurrence.getTncnAmount()),
          inspectorNet,
          refunded,
          netInflow,
          occurrence.getPaymentStatus().name(),
          occurrence.getPaymentMethod(),
          occurrence.getPaymentReference(),
          occurrence.getPaymentConfirmedAt(),
          occurrence.getPayoutStatus().name(),
          occurrence.getPayoutReference(),
          occurrence.getPayoutRecordedAt(),
          payoutUpdatedAt);
    }
  }

  public static BigDecimal resolveInspectorNetPayout(ServiceRequestOccurrence occurrence) {
    if (occurrence.getFinalPayoutAmount() != null) {
      return occurrence.getFinalPayoutAmount().setScale(2, RoundingMode.HALF_UP);
    }
    BigDecimal remaining = occurrence.getRemainingInspectorNetPayout();
    if (remaining != null) {
      return remaining.max(BigDecimal.ZERO);
    }
    return nonNullZero(occurrence.getInspectorNetPayout());
  }

  public static BigDecimal completedCommissionReduction(ServiceRequestOccurrence occurrence) {
    BigDecimal reduction = BigDecimal.ZERO;
    for (ServiceRefund refund : occurrence.getRefunds()) {
      if (refund.getStatus() == ServiceRefund.RefundStatus.COMPLETED
          && refund.getPlatformCommissionAdjustment() != null) {
        reduction = reduction.add(refund.getPlatformCommissionAdjustment());
      }
    }
    return reduction.setScale(2, RoundingMode.HALF_UP);
  }

  public static BigDecimal nonNullZero(BigDecimal value) {
    return value == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : value;
  }

  public static ManagerFinanceSummary summarize(List<ServiceRequestOccurrence> allRecords) {
    long paidTransactionCount = 0;
    BigDecimal totalPaid = BigDecimal.ZERO;
    BigDecimal totalRefunded = BigDecimal.ZERO;
    BigDecimal totalCommission = BigDecimal.ZERO;
    BigDecimal totalVat = BigDecimal.ZERO;
    BigDecimal totalTncn = BigDecimal.ZERO;
    BigDecimal totalInspector = BigDecimal.ZERO;
    BigDecimal paidInspector = BigDecimal.ZERO;
    BigDecimal pendingInspector = BigDecimal.ZERO;

    for (ServiceRequestOccurrence occ : allRecords) {
      boolean customerPaid =
          occ.getPaymentStatus() == ServiceRequestOccurrence.OccurrencePaymentStatus.PAID;
      if (customerPaid) {
        paidTransactionCount++;
        BigDecimal paid = nonNullZero(occ.getPaymentAmount());
        BigDecimal refunded = nonNullZero(occ.getRefundedAmount());
        BigDecimal grossComm = nonNullZero(occ.getCommissionAmount());
        BigDecimal netComm =
            grossComm.subtract(completedCommissionReduction(occ)).max(BigDecimal.ZERO);
        totalPaid = totalPaid.add(paid);
        totalRefunded = totalRefunded.add(refunded);
        totalCommission = totalCommission.add(netComm);
        totalVat = totalVat.add(nonNullZero(occ.getVatAmount()));
      }
      BigDecimal inspectorNet = resolveInspectorNetPayout(occ);
      totalTncn = totalTncn.add(nonNullZero(occ.getTncnAmount()));
      totalInspector = totalInspector.add(inspectorNet);
      if (occ.getPayoutStatus() == OccurrencePayoutStatus.PAID) {
        paidInspector = paidInspector.add(inspectorNet);
      } else {
        pendingInspector = pendingInspector.add(inspectorNet);
      }
    }

    BigDecimal netInflow = totalPaid.subtract(totalRefunded).max(BigDecimal.ZERO);
    return new ManagerFinanceSummary(
        "VND",
        paidTransactionCount,
        totalPaid.setScale(2, RoundingMode.HALF_UP),
        totalRefunded.setScale(2, RoundingMode.HALF_UP),
        netInflow.setScale(2, RoundingMode.HALF_UP),
        totalCommission.setScale(2, RoundingMode.HALF_UP),
        totalVat.setScale(2, RoundingMode.HALF_UP),
        totalTncn.setScale(2, RoundingMode.HALF_UP),
        totalInspector.setScale(2, RoundingMode.HALF_UP),
        paidInspector.setScale(2, RoundingMode.HALF_UP),
        pendingInspector.setScale(2, RoundingMode.HALF_UP));
  }
}
