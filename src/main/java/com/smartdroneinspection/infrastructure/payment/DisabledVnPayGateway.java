package com.smartdroneinspection.infrastructure.payment;

import com.smartdroneinspection.contracts.gateway.VnPayGateway;
import com.smartdroneinspection.shared.exception.BusinessException;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Fallback gateway when VNPay is disabled in configuration.
 *
 * <p>Fails closed for online checkout attempts with HTTP 503 PAYMENT_GATEWAY_DISABLED while keeping
 * PayOS and corporate wire transfer available.
 */
@Component
@ConditionalOnProperty(
    prefix = "app.payment.vnpay",
    name = "enabled",
    havingValue = "false",
    matchIfMissing = true)
public class DisabledVnPayGateway implements VnPayGateway {

  @Override
  public VnPayPaymentLink createPaymentUrl(
      long orderCode, long amount, String description, String clientIp, String returnUrl) {
    throw unavailable();
  }

  @Override
  public boolean verifySecureHash(Map<String, String> params, String receivedSecureHash) {
    return false;
  }

  private static BusinessException unavailable() {
    return new BusinessException(
        HttpStatus.SERVICE_UNAVAILABLE,
        "PAYMENT_GATEWAY_DISABLED",
        "Online payments via VNPay are not enabled. Configure VNPAY_ENABLED=true with merchant credentials or use PayOS / corporate wire transfer.");
  }
}
