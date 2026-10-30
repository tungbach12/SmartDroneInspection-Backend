package com.smartdroneinspection.infrastructure.payment;

import com.smartdroneinspection.contracts.gateway.PayOSGateway;
import com.smartdroneinspection.shared.exception.BusinessException;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Fallback gateway when PayOS is disabled in configuration.
 *
 * <p>Fails closed for online checkout attempts with HTTP 503 PAYMENT_GATEWAY_DISABLED while keeping
 * manual corporate wire transfer available.
 */
@Component
@ConditionalOnProperty(
    prefix = "app.payment.payos",
    name = "enabled",
    havingValue = "false",
    matchIfMissing = true)
public class DisabledPayOSGateway implements PayOSGateway {

  @Override
  public PayOSPaymentLink createPaymentLink(
      long orderCode, long amount, String description, String returnUrl, String cancelUrl) {
    throw unavailable();
  }

  @Override
  public boolean verifyWebhookSignature(Map<String, Object> data, String receivedSignature) {
    return false;
  }

  private static BusinessException unavailable() {
    return new BusinessException(
        HttpStatus.SERVICE_UNAVAILABLE,
        "PAYMENT_GATEWAY_DISABLED",
        "Online payments via PayOS are not enabled. Use corporate wire transfer instead.");
  }
}
