package com.smartdroneinspection.infrastructure.ai;

import java.net.http.HttpClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

/**
 * Advisory image detection through an OpenAI-compatible chat endpoint. This adapter is optional and
 * is configured alongside the YOLO adapter, which stays available for a self-hosted detector.
 */
@Configuration(proxyBeanMethods = false)
@Conditional(SingleAiInferenceProviderCondition.class)
@EnableConfigurationProperties(CompatibleVisionProperties.class)
@ConditionalOnProperty(prefix = "app.ai.compatible-vision", name = "enabled", havingValue = "true")
class AiInferenceConfiguration {

  @Bean
  RestClient compatibleVisionRestClient(CompatibleVisionProperties properties) {
    if (blank(properties.baseUrl())
        || blank(properties.model())
        || blank(properties.apiKey())
        || properties.timeout() == null
        || properties.timeout().isNegative()
        || properties.timeout().isZero()) {
      throw new IllegalStateException(
          "Compatible vision endpoint, model name, API key, and positive timeout are required.");
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
  OpenAiCompatibleInferenceClient openAiCompatibleInferenceClient(
      RestClient compatibleVisionRestClient, CompatibleVisionProperties properties) {
    return new OpenAiCompatibleInferenceClient(
        compatibleVisionRestClient, properties.apiKey(), properties.model(), new ObjectMapper());
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
