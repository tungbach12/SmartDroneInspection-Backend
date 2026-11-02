package com.smartdroneinspection.contracts.api.dto.response;

/**
 * Payer-facing response containing VNPay checkout URL details.
 *
 * @param orderCode unique numeric transaction code assigned to this occurrence
 * @param checkoutUrl signed browser redirect URL hosted by VNPay
 * @param amount total payment amount in VND
 * @param txnRef VNPay vnp_TxnRef identifier
 */
public record VnPayPaymentLinkResponse(
    long orderCode, String checkoutUrl, long amount, String txnRef) {}
