package com.smartdroneinspection.infrastructure.ai;

import java.net.http.HttpClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MaintenanceScopeAdvisorProperties.class)
@ConditionalOnProperty(prefix = "app.maintenance-advisor", name = "enabled", havingValue = "true")
class MaintenanceScopeAdvisorConfiguration {

  @Bean
  RestClient maintenanceAdvisorRestClient(MaintenanceScopeAdvisorProperties properties) {
    if (blank(properties.baseUrl())
        || blank(properties.model())
        || properties.timeout() == null
        || properties.timeout().isNegative()
        || properties.timeout().isZero()) {
      throw new IllegalStateException(
          "Maintenance advisor endpoint, model name, and positive timeout are required.");
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
  MaintenanceScopeAdvisorClient maintenanceScopeAdvisorClient(
      RestClient maintenanceAdvisorRestClient, MaintenanceScopeAdvisorProperties properties) {
    return new MaintenanceScopeAdvisorClient(
        maintenanceAdvisorRestClient, properties.apiKey(), properties.model());
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
