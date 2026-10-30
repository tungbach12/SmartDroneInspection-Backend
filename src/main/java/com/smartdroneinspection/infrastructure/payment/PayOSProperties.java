package com.smartdroneinspection.infrastructure.payment;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration for the PayOS VietQR payment gateway.
 *
 * <p>PayOS facilitates instant automated bank transfers (VietQR). The credentials are deployment
 * secrets and are never committed. Leave {@code enabled} off to keep the platform in manual
 * wire-transfer-only mode, which is the safe default for a deployment without PayOS merchant keys.
 */
@ConfigurationProperties("app.payment.payos")
public record PayOSProperties(
    @DefaultValue("false") boolean enabled,
    @DefaultValue("https://api-merchant.payos.vn") String baseUrl,
    String clientId,
    String apiKey,
    String checksumKey,
    String returnUrl,
    String cancelUrl,
    @DefaultValue("5s") Duration connectTimeout,
    @DefaultValue("15s") Duration readTimeout) {

  public PayOSProperties {
    if (baseUrl == null || baseUrl.isBlank()) {
      baseUrl = "https://api-merchant.payos.vn";
    }
    if (connectTimeout == null) {
      connectTimeout = Duration.ofSeconds(5);
    }
    if (readTimeout == null) {
      readTimeout = Duration.ofSeconds(15);
    }
  }
}
