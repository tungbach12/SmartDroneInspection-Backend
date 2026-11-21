package com.smartdroneinspection.infrastructure.payment;

import com.smartdroneinspection.contracts.gateway.SepayGateway;
import com.smartdroneinspection.shared.exception.BusinessException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Fallback gateway when Sepay is disabled in configuration.
 *
 * <p>Fails closed for online checkout attempts with HTTP 503 PAYMENT_GATEWAY_DISABLED while keeping
 * manual corporate wire transfer available.
 */
@Component
@ConditionalOnProperty(
    prefix = "app.payment.sepay",
    name = "enabled",
    havingValue = "false",
    matchIfMissing = true)
public class DisabledSepayGateway implements SepayGateway {

  @Override
  public SepayPaymentLink createPaymentLink(
      long orderCode,
      long amount,
      String description,
      String accountNumber,
      String bankName,
      String accountHolder) {
    throw unavailable();
  }

  @Override
  public boolean verifyWebhookApiKey(String receivedApiKey) {
    return false;
  }

  private static BusinessException unavailable() {
    return new BusinessException(
        HttpStatus.SERVICE_UNAVAILABLE,
        "PAYMENT_GATEWAY_DISABLED",
        "Online payments via Sepay are not enabled. Use corporate wire transfer instead.");
  }
}
