package com.smartdroneinspection.contracts.gateway;

import java.util.Map;

/** Settlement channel contract for VNPay online payments (v2.1.0). */
public interface VnPayGateway {

  /**
   * Generates a signed VNPay checkout URL (HMAC-SHA512) for an accepted occurrence.
   *
   * @param orderCode unique numeric transaction reference (vnp_TxnRef)
   * @param amount payment amount in VND
   * @param description human-readable order info (vnp_OrderInfo)
   * @param clientIp payer IPv4 address (vnp_IpAddr)
   * @param returnUrl return callback URL after checkout (vnp_ReturnUrl)
   * @return signed VNPay checkout details
   */
  VnPayPaymentLink createPaymentUrl(
      long orderCode, long amount, String description, String clientIp, String returnUrl);

  /** Resolves the return URL for a specific inspection and order code. */
  default String resolveReturnUrl(String inspectionId, long orderCode) {
    return null;
  }

  /** Resolves the frontend redirect URL after VNPay return callback verification. */
  default String resolveFrontendReturnUrl(
      String inspectionId, long orderCode, boolean success, String responseCode) {
    return null;
  }

  /**
   * Verifies authenticity of incoming VNPay Return/IPN query parameters using HMAC-SHA512.
   *
   * @param params query parameters map received from VNPay
   * @param receivedSecureHash the hex vnp_SecureHash value received from VNPay
   * @return true if valid, false otherwise
   */
  boolean verifySecureHash(Map<String, String> params, String receivedSecureHash);

  record VnPayPaymentLink(long orderCode, String checkoutUrl, long amount, String txnRef) {}
}
