package com.smartdroneinspection.infrastructure.ai;

import java.net.http.HttpClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@org.springframework.context.annotation.Conditional(SingleAiInferenceProviderCondition.class)
@EnableConfigurationProperties(YoloInferenceProperties.class)
@ConditionalOnProperty(prefix = "app.ai.yolo", name = "enabled", havingValue = "true")
class YoloInferenceConfiguration {

  @Bean
  RestClient yoloRestClient(YoloInferenceProperties properties) {
    if (blank(properties.baseUrl())
        || blank(properties.predictPath())
        || properties.connectTimeout() == null
        || properties.readTimeout() == null
        || properties.connectTimeout().isNegative()
        || properties.connectTimeout().isZero()
        || properties.readTimeout().isNegative()
        || properties.readTimeout().isZero()) {
      throw new IllegalStateException(
          "YOLO inference endpoint and positive timeouts are required.");
    }
    HttpClient httpClient =
        HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(properties.readTimeout());
    return RestClient.builder()
        .baseUrl(properties.baseUrl())
        .requestFactory(requestFactory)
        .build();
  }

  @Bean
  YoloInferenceClient yoloInferenceClient(
      RestClient yoloRestClient, YoloInferenceProperties properties) {
    return new YoloInferenceClient(yoloRestClient, properties.predictPath());
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
