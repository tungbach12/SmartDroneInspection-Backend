package com.smartdroneinspection.infrastructure.payment;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(VnPayProperties.class)
@ConditionalOnProperty(prefix = "app.payment.vnpay", name = "enabled", havingValue = "true")
class VnPayConfiguration {

  @Bean
  VnPayPaymentGateway vnPayPaymentGateway(VnPayProperties properties) {
    if (blank(properties.tmnCode())
        || blank(properties.hashSecret())
        || blank(properties.payUrl())) {
      throw new IllegalStateException(
          "VNPay tmnCode, hashSecret, and payUrl are required when VNPay is enabled.");
    }
    return new VnPayPaymentGateway(properties);
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
