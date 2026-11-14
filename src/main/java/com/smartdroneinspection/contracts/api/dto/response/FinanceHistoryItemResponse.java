package com.smartdroneinspection.contracts.api.dto.response;

import com.smartdroneinspection.contracts.domain.ServiceRequestOccurrence;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record FinanceHistoryItemResponse(
    UUID occurrenceId,
    UUID requestId,
    int occurrenceIndex,
    String requestDescription,
    UUID assetId,
    String assetName,
    UUID counterpartyId,
    String counterpartyName,
    String currency,
    BigDecimal amount,
    String status,
    String paymentMethod,
    String reference,
    Instant confirmedAt) {

  public static FinanceHistoryItemResponse customer(
      ServiceRequestOccurrence occurrence,
      String requestDescription,
      UUID assetId,
      String assetName,
      UUID inspectorId,
      String inspectorName) {
    return new FinanceHistoryItemResponse(
        occurrence.getId(),
        occurrence.getRequestId(),
        occurrence.getOccurrenceIndex(),
        requestDescription,
        assetId,
        assetName,
        inspectorId,
        inspectorName,
        occurrence.getCurrency(),
        occurrence.getPaymentAmount(),
        occurrence.getPaymentStatus().name(),
        occurrence.getPaymentMethod(),
        occurrence.getPaymentReference(),
        occurrence.getPaymentConfirmedAt());
  }

  public static FinanceHistoryItemResponse inspector(
      ServiceRequestOccurrence occurrence,
      String requestDescription,
      UUID assetId,
      String assetName,
      UUID customerId,
      String customerName) {
    return new FinanceHistoryItemResponse(
        occurrence.getId(),
        occurrence.getRequestId(),
        occurrence.getOccurrenceIndex(),
        requestDescription,
        assetId,
        assetName,
        customerId,
        customerName,
        occurrence.getCurrency(),
        occurrence.getFinalPayoutAmount(),
        occurrence.getPayoutStatus().name(),
        occurrence.getPaymentMethod(),
        occurrence.getPayoutReference(),
        occurrence.getPayoutRecordedAt());
  }
}
