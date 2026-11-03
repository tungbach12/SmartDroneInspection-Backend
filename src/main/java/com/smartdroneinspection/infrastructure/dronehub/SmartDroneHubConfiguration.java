package com.smartdroneinspection.infrastructure.dronehub;

import java.net.http.HttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SmartDroneHubProperties.class)
class SmartDroneHubConfiguration {

  @Bean
  RestClient smartDroneHubRestClient(SmartDroneHubProperties properties) {
    if (properties.baseUrl() == null
        || properties.baseUrl().isBlank()
        || properties.connectTimeout() == null
        || properties.connectTimeout().isNegative()
        || properties.connectTimeout().isZero()
        || properties.readTimeout() == null
        || properties.readTimeout().isNegative()
        || properties.readTimeout().isZero()) {
      throw new IllegalStateException("SmartDroneHub base URL and positive timeouts are required.");
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
  SmartDroneHubClient smartDroneHubClient(RestClient smartDroneHubRestClient) {
    return new SmartDroneHubClient(smartDroneHubRestClient);
  }
}
