package com.smartdroneinspection.shared.api;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Shared boundary interface for customer acceptance, payment, and auto-disbursement of a published
 * inspection report.
 */
public interface InspectionReportAcceptancePort {

  /** Accepts the published report version for the occurrence associated with this inspection. */
  void acceptReport(UUID actorId, UUID inspectionId, UUID reportVersionId);

  /** Creates a PayOS checkout link for the occurrence associated with this inspection. */
  PayOSCheckoutDetails createPayOSPaymentLink(UUID actorId, UUID inspectionId);

  /** Creates a VNPay checkout link for the occurrence associated with this inspection. */
  default VnPayCheckoutDetails createVnPayPaymentLink(
      UUID actorId, UUID inspectionId, String clientIp) {
    throw new UnsupportedOperationException("VNPay payment link creation is not supported");
  }

  /** Retrieves commercial payment, commission, and payout status for the occurrence. */
  default PaymentStatusDetails getPaymentStatus(UUID actorId, UUID inspectionId) {
    return new PaymentStatusDetails(false, false, "NOT_APPLICABLE", null, null, null);
  }

  /**
   * Batch retrieves commercial payment, commission, and payout status for a page of inspections.
   */
  default Map<UUID, PaymentStatusDetails> getPaymentStatuses(Collection<UUID> inspectionIds) {
    return Map.of();
  }

  record PayOSCheckoutDetails(
      long orderCode, long amount, String paymentLinkId, String checkoutUrl, String qrCode) {}

  record VnPayCheckoutDetails(long orderCode, long amount, String txnRef, String checkoutUrl) {}

  record PaymentStatusDetails(
      boolean reportAccepted,
      boolean paid,
      String paymentStatus,
      Long orderCode,
      Long amount,
      String checkoutUrl,
      BigDecimal serviceBaseAmount,
      BigDecimal vatRate,
      BigDecimal vatAmount,
      BigDecimal commissionRate,
      BigDecimal commissionAmount,
      BigDecimal inspectorGrossPayout,
      BigDecimal tncnRate,
      BigDecimal tncnAmount,
      BigDecimal inspectorNetPayout,
      String payoutStatus,
      String payoutReference,
      String requestDescription,
      String paymentMethod) {

    public PaymentStatusDetails(
        boolean reportAccepted,
        boolean paid,
        String paymentStatus,
        Long orderCode,
        Long amount,
        String checkoutUrl) {
      this(
          reportAccepted,
          paid,
          paymentStatus,
          orderCode,
          amount,
          checkoutUrl,
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
          null);
    }
  }
}
