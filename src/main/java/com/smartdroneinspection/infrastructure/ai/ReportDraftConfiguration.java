package com.smartdroneinspection.infrastructure.ai;

import java.net.http.HttpClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ReportDraftProperties.class)
@ConditionalOnProperty(prefix = "app.report-draft", name = "enabled", havingValue = "true")
class ReportDraftConfiguration {

  @Bean
  RestClient reportDraftRestClient(ReportDraftProperties properties) {
    if (blank(properties.baseUrl())
        || blank(properties.model())
        || blank(properties.apiKey())
        || properties.timeout() == null
        || properties.timeout().isNegative()
        || properties.timeout().isZero()) {
      throw new IllegalStateException(
          "Report draft endpoint, API key, model name, and positive timeout are required.");
    }
    HttpClient httpClient = HttpClient.newBuilder().connectTimeout(properties.timeout()).build();
    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(properties.timeout());
    return RestClient.builder()
        .baseUrl(properties.baseUrl())
        .requestFactory(requestFactory)
        .build();
  }

  @Bean
  ReportDraftClient reportDraftClient(
      RestClient reportDraftRestClient, ReportDraftProperties properties) {
    return new ReportDraftClient(reportDraftRestClient, properties.apiKey(), properties.model());
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
