package com.smartdroneinspection.infrastructure.payment;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SepayProperties.class)
@ConditionalOnProperty(prefix = "app.payment.sepay", name = "enabled", havingValue = "true")
class SepayConfiguration {

  @Bean
  SepayPaymentGateway sepayPaymentGateway(SepayProperties properties) {
    // Fail at startup rather than on the first customer payment attempt: a VietQR code cannot be
    // built without the receiving account, and the webhook cannot be authenticated without a key.
    require(properties.apiKey(), "SEPAY_API_KEY");
    require(properties.accountNumber(), "SEPAY_ACCOUNT_NUMBER");
    require(properties.bankName(), "SEPAY_BANK_NAME");
    return new SepayPaymentGateway(properties);
  }

  private static void require(String value, String name) {
    if (value == null || value.isBlank()) {
      throw new IllegalStateException(
          name + " is required when Sepay is enabled (app.payment.sepay.enabled=true).");
    }
  }
}
