package com.smartdroneinspection.infrastructure.payment;

import java.net.http.HttpClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PayPalProperties.class)
@ConditionalOnProperty(prefix = "app.payment.paypal", name = "enabled", havingValue = "true")
class PayPalConfiguration {

  @Bean
  RestClient payPalRestClient(PayPalProperties properties) {
    String expectedBaseUrl =
        "sandbox".equalsIgnoreCase(properties.mode())
            ? "https://api-m.sandbox.paypal.com"
            : "https://api-m.paypal.com";
    if (blank(properties.clientId())
        || blank(properties.clientSecret())
        || !expectedBaseUrl.equals(properties.baseUrl())
        || properties.connectTimeout() == null
        || properties.readTimeout() == null
        || properties.connectTimeout().isNegative()
        || properties.connectTimeout().isZero()
        || properties.readTimeout().isNegative()
        || properties.readTimeout().isZero()) {
      throw new IllegalStateException(
          "PayPal client id, client secret, and base URL are required when the PayPal channel is"
              + " enabled.");
    }
    HttpClient httpClient =
        HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(properties.connectTimeout())
            .build();
    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(properties.readTimeout());
    return RestClient.builder()
        .baseUrl(properties.baseUrl())
        .requestFactory(requestFactory)
        .build();
  }

  @Bean
  PayPalPaymentGateway payPalPaymentGateway(
      RestClient payPalRestClient, PayPalProperties properties, ObjectMapper objectMapper) {
    return new PayPalPaymentGateway(payPalRestClient, properties, objectMapper);
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
