package com.smartdroneinspection.infrastructure.ai;

import java.net.http.HttpClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

/** Optional outbound adapter for the embeddings endpoint behind the inspections-owned SPI. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DroneKnowledgeProperties.class)
@ConditionalOnProperty(prefix = "app.knowledge-ai", name = "enabled", havingValue = "true")
class DroneKnowledgeConfiguration {

  @Bean
  RestClient droneKnowledgeRestClient(DroneKnowledgeProperties properties) {
    if (blank(properties.baseUrl())
        || blank(properties.embeddingModel())
        || properties.timeout() == null
        || properties.timeout().isNegative()
        || properties.timeout().isZero()) {
      throw new IllegalStateException(
          "Knowledge AI endpoint, embedding model name, and positive timeout are required.");
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
  DroneKnowledgeClient droneKnowledgeClient(
      RestClient droneKnowledgeRestClient, DroneKnowledgeProperties properties) {
    return new DroneKnowledgeClient(
        droneKnowledgeRestClient,
        properties.apiKey(),
        properties.embeddingModel(),
        new ObjectMapper());
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
