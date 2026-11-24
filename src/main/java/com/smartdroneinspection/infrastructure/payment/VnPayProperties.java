package com.smartdroneinspection.infrastructure.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
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
    @DefaultValue("http://localhost:3000/client/inspections/{inspectionId}/settlement")
        String frontendReturnUrl,
    @DefaultValue("smartdrone://inspections/{inspectionId}/settlement") String mobileReturnUrl,
    @DefaultValue("2.1.0") String version,
    @DefaultValue("pay") String command,
    @DefaultValue("other") String orderType,
    @DefaultValue("VND") String currCode,
    @DefaultValue("vn") String locale,
    @DefaultValue("15") int expireMinutes) {

  @ConstructorBinding
  public VnPayProperties {
    if (payUrl == null || payUrl.isBlank()) {
      payUrl = "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html";
    }
    if (returnUrl == null || returnUrl.isBlank()) {
      returnUrl = "http://localhost:8080/api/v1/webhooks/vnpay/return";
    }
    if (frontendReturnUrl == null || frontendReturnUrl.isBlank()) {
      frontendReturnUrl = "http://localhost:3000/client/inspections/{inspectionId}/settlement";
    }
    if (mobileReturnUrl == null || mobileReturnUrl.isBlank()) {
      mobileReturnUrl = "smartdrone://inspections/{inspectionId}/settlement";
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

  public VnPayProperties(
      boolean enabled,
      String payUrl,
      String tmnCode,
      String hashSecret,
      String returnUrl,
      String frontendReturnUrl,
      String version,
      String command,
      String orderType,
      String currCode,
      String locale,
      int expireMinutes) {
    this(
        enabled,
        payUrl,
        tmnCode,
        hashSecret,
        returnUrl,
        frontendReturnUrl,
        "smartdrone://inspections/{inspectionId}/settlement",
        version,
        command,
        orderType,
        currCode,
        locale,
        expireMinutes);
  }
}
