package com.smartdroneinspection.contracts.api.dto.response;

/**
 * Payer-facing response containing Sepay VietQR payment details.
 *
 * @param orderCode unique numeric transaction code assigned to this occurrence
 * @param checkoutUrl browser-rendered VietQR image URL
 * @param qrCode raw transfer content string to display or scan
 * @param amount total payment amount in VND
 * @param paymentLinkId Sepay payment identifier (the order code)
 */
public record SepayPaymentLinkResponse(
    long orderCode, String checkoutUrl, String qrCode, long amount, String paymentLinkId) {}
