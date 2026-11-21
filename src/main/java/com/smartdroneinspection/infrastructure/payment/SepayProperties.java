package com.smartdroneinspection.infrastructure.payment;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration for the Sepay VietQR payment gateway.
 *
 * <p>Sepay notifies the merchant of incoming bank transfers via a webhook and renders a VietQR
 * image for the merchant account. Credentials are deployment secrets and are never committed. Leave
 * {@code enabled} off to keep the platform in manual wire-transfer-only mode, which is the safe
 * default for a deployment without Sepay credentials.
 */
@ConfigurationProperties("app.payment.sepay")
public record SepayProperties(
    @DefaultValue("false") boolean enabled,
    @DefaultValue("https://vietqr.app") String qrBaseUrl,
    String apiKey,
    String accountNumber,
    String bankName,
    String accountHolder,
    String returnUrl,
    String cancelUrl,
    @DefaultValue("5s") Duration connectTimeout,
    @DefaultValue("15s") Duration readTimeout) {

  public SepayProperties {
    if (qrBaseUrl == null || qrBaseUrl.isBlank()) {
      qrBaseUrl = "https://vietqr.app";
    }
    if (connectTimeout == null) {
      connectTimeout = Duration.ofSeconds(5);
    }
    if (readTimeout == null) {
      readTimeout = Duration.ofSeconds(15);
    }
  }
}
