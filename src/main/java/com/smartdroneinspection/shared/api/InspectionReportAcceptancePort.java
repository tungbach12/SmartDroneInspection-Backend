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

  /**
   * Returns true when a linked commercial occurrence exists whose Inspector payout is not yet
   * settled (PAID), so report approval must pause at APPROVED until Manager pays Inspector.
   */
  default boolean requiresPayoutBeforePublish(UUID inspectionId) {
    return false;
  }

  /** Marks the linked occurrence's Inspector payout eligible once Manager approves the report. */
  default void onReportApproved(UUID inspectionId) {
    onReportPublished(inspectionId);
  }

  /**
   * Marks the linked occurrence's Inspector payout eligible once the Manager publishes the report.
   */
  default void onReportPublished(UUID inspectionId) {}

  /** Creates a Sepay VietQR code for the occurrence associated with this inspection. */
  SepayCheckoutDetails createSepayPaymentLink(UUID actorId, UUID inspectionId);

  /** Creates a VNPay checkout link for the occurrence associated with this inspection. */
  default VnPayCheckoutDetails createVnPayPaymentLink(
      UUID actorId, UUID inspectionId, String clientIp) {
    throw new UnsupportedOperationException("VNPay payment link creation is not supported");
  }

  /** Creates a VNPay checkout link with optional mobile deep-link return routing. */
  default VnPayCheckoutDetails createVnPayPaymentLink(
      UUID actorId, UUID inspectionId, String clientIp, boolean mobileClient) {
    return createVnPayPaymentLink(actorId, inspectionId, clientIp);
  }

  /** Clears an unpaid, resolvable gateway order on the occurrence so another method can be used. */
  default void cancelUnpaidPaymentOrder(UUID actorId, UUID inspectionId) {
    throw new UnsupportedOperationException("Cancelling unpaid payment order is not supported");
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

  record SepayCheckoutDetails(
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
      String paymentMethod,
      java.time.Instant paymentDueAt,
      java.time.Instant paymentConfirmedAt) {

    public PaymentStatusDetails(
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
        String paymentMethod,
        java.time.Instant paymentDueAt) {
      this(
          reportAccepted,
          paid,
          paymentStatus,
          orderCode,
          amount,
          checkoutUrl,
          serviceBaseAmount,
          vatRate,
          vatAmount,
          commissionRate,
          commissionAmount,
          inspectorGrossPayout,
          tncnRate,
          tncnAmount,
          inspectorNetPayout,
          payoutStatus,
          payoutReference,
          requestDescription,
          paymentMethod,
          paymentDueAt,
          null);
    }

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
          null,
          null,
          null);
    }
  }
}
