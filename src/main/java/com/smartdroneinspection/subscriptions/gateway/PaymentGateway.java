package com.smartdroneinspection.subscriptions.gateway;

import java.math.BigDecimal;
import java.util.Map;

/**
 * The settlement channel the platform charges enterprise subscriptions through.
 *
 * <p>The contract is deliberately narrow: the platform asks the gateway to create an order for a
 * price it computed itself, and to capture an order. The gateway answers with what actually
 * happened, including the amount and currency it settled. Nothing that a browser supplies is
 * treated as payment evidence.
 */
public interface PaymentGateway {

  /**
   * Creates an order for {@code amount} in {@code currency}.
   *
   * @param amount the amount the platform expects to be paid
   * @param currency the ISO currency code the platform expects
   * @param description a human-readable description shown to the payer
   * @return the created order, carrying the gateway's own order id
   */
  PaymentGatewayOrder createOrder(BigDecimal amount, String currency, String description);

  /**
   * Captures a previously authorized order.
   *
   * @param orderId the gateway order id to capture
   * @return what the gateway actually captured, including its own capture id and status
   */
  Capture captureOrder(String orderId);

  /**
   * Verifies that a webhook really came from the gateway.
   *
   * @param headers the transport headers the gateway signs, such as its transmission id and
   *     signature
   * @param rawPayload the exact body the gateway sent
   * @return true only when the gateway confirms the notification is authentic
   */
  boolean verifyWebhookSignature(Map<String, String> headers, String rawPayload);

  /**
   * The order as the gateway created it.
   *
   * @param orderId the gateway's order id
   * @param status the gateway's order status
   * @param approvalUrl the payer-facing approval link, when the gateway supplies one
   */
  record PaymentGatewayOrder(String orderId, String status, String approvalUrl) {

    public PaymentGatewayOrder(String orderId, String status) {
      this(orderId, status, null);
    }
  }

  /**
   * A capture as the gateway reported it.
   *
   * @param orderId the gateway order id the capture belongs to
   * @param captureId the gateway capture id, absent when no capture happened
   * @param status the capture status, for example {@code COMPLETED} or {@code DECLINED}
   * @param amount the amount the gateway settled
   * @param currency the currency the gateway settled
   * @param payerEmail the payer's email address, when the gateway reports one
   */
  record Capture(
      String orderId,
      String captureId,
      String status,
      String amount,
      String currency,
      String payerEmail) {}
}
