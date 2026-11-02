package com.smartdroneinspection.infrastructure.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration for the VNPay online payment gateway (v2.1.0).
 *
 * <p>Supports VNPay Sandbox ({@code https://sandbox.vnpayment.vn/paymentv2/vpcpay.html}) and
 * Production endpoints using merchant {@code vnp_TmnCode} and {@code vnp_HashSecret} (HMAC-SHA512).
 */
@ConfigurationProperties("app.payment.vnpay")
public record VnPayProperties(
    @DefaultValue("false") boolean enabled,
    @DefaultValue("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html") String payUrl,
    String tmnCode,
    String hashSecret,
    @DefaultValue("http://localhost:8080/api/v1/webhooks/vnpay/return") String returnUrl,
    @DefaultValue("http://localhost:3000/client/reports/{inspectionId}") String frontendReturnUrl,
    @DefaultValue("2.1.0") String version,
    @DefaultValue("pay") String command,
    @DefaultValue("other") String orderType,
    @DefaultValue("VND") String currCode,
    @DefaultValue("vn") String locale,
    @DefaultValue("15") int expireMinutes) {

  public VnPayProperties {
    if (payUrl == null || payUrl.isBlank()) {
      payUrl = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html";
    }
    if (returnUrl == null || returnUrl.isBlank()) {
      returnUrl = "http://localhost:8080/api/v1/webhooks/vnpay/return";
    }
    if (frontendReturnUrl == null || frontendReturnUrl.isBlank()) {
      frontendReturnUrl = "http://localhost:3000/client/reports/{inspectionId}";
    }
    if (version == null || version.isBlank()) {
      version = "2.1.0";
    }
    if (command == null || command.isBlank()) {
      command = "pay";
    }
    if (orderType == null || orderType.isBlank()) {
      orderType = "other";
    }
    if (currCode == null || currCode.isBlank()) {
      currCode = "VND";
    }
    if (locale == null || locale.isBlank()) {
      locale = "vn";
    }
    if (expireMinutes <= 0) {
      expireMinutes = 15;
    }
  }
}
