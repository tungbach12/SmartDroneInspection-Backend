package com.smartdroneinspection.contracts.gateway;

import java.util.Map;

/** Settlement channel contract for PayOS VietQR payments. */
public interface PayOSGateway {

  /**
   * Requests a new checkout link and VietQR payload from PayOS.
   *
   * @param orderCode unique numeric transaction identifier
   * @param amount payment amount in VND
   * @param description human-readable payment description (max 25 chars)
   * @param returnUrl browser redirect URL after successful checkout
   * @param cancelUrl browser redirect URL if payer cancels
   * @return payment link details including checkoutUrl and qrCode
   */
  PayOSPaymentLink createPaymentLink(
      long orderCode, long amount, String description, String returnUrl, String cancelUrl);

  /**
   * Retrieves existing payment link details by orderCode.
   *
   * @param orderCode unique numeric transaction identifier
   * @return payment link details or null if not found
   */
  default PayOSPaymentLink getPaymentLink(long orderCode) {
    return null;
  }

  /**
   * Resolves the return URL for a specific inspection, substituting configured template variables.
   */
  default String resolveReturnUrl(String inspectionId, long orderCode) {
    return null;
  }

  /**
   * Resolves the cancel URL for a specific inspection, substituting configured template variables.
   */
  default String resolveCancelUrl(String inspectionId) {
    return null;
  }

  /**
   * Verifies authenticity of an incoming PayOS webhook payload.
   *
   * @param data the data dictionary from webhook payload
   * @param receivedSignature the hex signature from webhook payload
   * @return true if valid, false otherwise
   */
  boolean verifyWebhookSignature(Map<String, Object> data, String receivedSignature);

  record PayOSPaymentLink(
      long orderCode,
      String checkoutUrl,
      String qrCode,
      long amount,
      String paymentLinkId,
      String status) {}
}
