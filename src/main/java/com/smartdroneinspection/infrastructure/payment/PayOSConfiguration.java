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
@EnableConfigurationProperties(PayOSProperties.class)
@ConditionalOnProperty(prefix = "app.payment.payos", name = "enabled", havingValue = "true")
class PayOSConfiguration {

  @Bean
  RestClient payOSRestClient(PayOSProperties properties) {
    if (blank(properties.clientId())
        || blank(properties.apiKey())
        || blank(properties.checksumKey())
        || blank(properties.baseUrl())
        || properties.connectTimeout() == null
        || properties.readTimeout() == null
        || properties.connectTimeout().isNegative()
        || properties.connectTimeout().isZero()
        || properties.readTimeout().isNegative()
        || properties.readTimeout().isZero()) {
      throw new IllegalStateException(
          "PayOS client id, api key, checksum key, and base URL are required when PayOS is enabled.");
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
  PayOSPaymentGateway payOSPaymentGateway(
      RestClient payOSRestClient, PayOSProperties properties, ObjectMapper objectMapper) {
    return new PayOSPaymentGateway(payOSRestClient, properties, objectMapper);
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
