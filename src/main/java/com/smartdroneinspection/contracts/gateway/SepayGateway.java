package com.smartdroneinspection.contracts.gateway;

/** Settlement channel contract for Sepay VietQR payments. */
public interface SepayGateway {

  /**
   * Builds a VietQR image URL and raw payload for an occurrence.
   *
   * @param orderCode unique numeric transaction identifier (used in the transfer description)
   * @param amount payment amount in VND
   * @param description human-readable payment description
   * @param accountNumber merchant bank account number
   * @param bankName merchant bank short name (e.g. MB, VCB)
   * @param accountHolder merchant account holder name
   * @return payment link details including qrImageUrl and qrPayload
   */
  SepayPaymentLink createPaymentLink(
      long orderCode,
      long amount,
      String description,
      String accountNumber,
      String bankName,
      String accountHolder);

  /** Builds a VietQR image URL targeting the Inspector's receiving bank account for payout. */
  default SepayPaymentLink createPayoutLink(
      long payoutOrderCode,
      long amount,
      String accountNumber,
      String bankName,
      String accountHolder) {
    return createPaymentLink(
        payoutOrderCode,
        amount,
        "SDIPAY" + payoutOrderCode,
        accountNumber,
        bankName,
        accountHolder);
  }

  /**
   * Verifies authenticity of an incoming Sepay webhook request.
   *
   * @param receivedApiKey the value of the {@code Authorization: Apikey ...} header
   * @return true if valid, false otherwise
   */
  boolean verifyWebhookApiKey(String receivedApiKey);

  /**
   * Configured merchant receiving bank account number, used to verify that an inbound webhook
   * actually credited the platform's account. Null when not configured.
   */
  default String expectedAccountNumber() {
    return null;
  }

  record SepayPaymentLink(
      long orderCode,
      String checkoutUrl,
      String qrCode,
      long amount,
      String paymentLinkId,
      String status) {}
}
