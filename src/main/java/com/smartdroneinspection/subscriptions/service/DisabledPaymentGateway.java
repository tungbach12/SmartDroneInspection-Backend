package com.smartdroneinspection.subscriptions.service;

import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.subscriptions.gateway.PaymentGateway;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Keeps wire-only deployments healthy while failing closed for unavailable online checkout. */
@Component
@ConditionalOnProperty(
    prefix = "app.payment.paypal",
    name = "enabled",
    havingValue = "false",
    matchIfMissing = true)
class DisabledPaymentGateway implements PaymentGateway {

  @Override
  public PaymentGatewayOrder createOrder(BigDecimal amount, String currency, String description) {
    throw unavailable();
  }

  @Override
  public Capture captureOrder(String orderId) {
    throw unavailable();
  }

  @Override
  public boolean verifyWebhookSignature(Map<String, String> headers, String rawPayload) {
    return false;
  }

  private static BusinessException unavailable() {
    return new BusinessException(
        HttpStatus.SERVICE_UNAVAILABLE,
        "PAYMENT_GATEWAY_DISABLED",
        "Online payments are not enabled. Use corporate wire transfer instead.");
  }
}
