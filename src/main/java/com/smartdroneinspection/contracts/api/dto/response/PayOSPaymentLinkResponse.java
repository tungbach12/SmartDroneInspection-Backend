package com.smartdroneinspection.contracts.api.dto.response;

/**
 * Payer-facing response containing PayOS VietQR payment link details.
 *
 * @param orderCode unique numeric transaction code assigned to this occurrence
 * @param checkoutUrl browser checkout redirect URL hosted by PayOS
 * @param qrCode raw VietQR payload string to display or scan
 * @param amount total payment amount in VND
 * @param paymentLinkId PayOS payment link identifier
 */
public record PayOSPaymentLinkResponse(
    long orderCode, String checkoutUrl, String qrCode, long amount, String paymentLinkId) {}
