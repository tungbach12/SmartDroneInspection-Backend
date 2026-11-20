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
    String chatOrFallbackBaseUrl =
        blank(properties.baseUrl()) ? properties.embeddingBaseUrl() : properties.baseUrl();
    if (blank(chatOrFallbackBaseUrl)
        || blank(properties.embeddingModel())
        || properties.timeout() == null
        || properties.timeout().isNegative()
        || properties.timeout().isZero()) {
      throw new IllegalStateException(
          "Knowledge AI endpoint, embedding model name, and positive timeout are required.");
    }
    return buildClient(chatOrFallbackBaseUrl, properties.timeout());
  }

  @Bean
  DroneKnowledgeClient droneKnowledgeClient(
      RestClient droneKnowledgeRestClient, DroneKnowledgeProperties properties) {
    RestClient chatClient = blank(properties.baseUrl()) ? null : droneKnowledgeRestClient;
    RestClient embeddingClient =
        blank(properties.embeddingBaseUrl())
            ? droneKnowledgeRestClient
            : buildClient(properties.embeddingBaseUrl(), properties.timeout());
    String embeddingKey =
        blank(properties.embeddingBaseUrl()) ? properties.apiKey() : properties.embeddingApiKey();
    return new DroneKnowledgeClient(
        chatClient,
        properties.apiKey(),
        embeddingClient,
        embeddingKey,
        properties.embeddingModel(),
        properties.chatModel(),
        new ObjectMapper());
  }

  private static RestClient buildClient(String baseUrl, java.time.Duration timeout) {
    HttpClient httpClient =
        HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(timeout)
            .build();
    JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
    requestFactory.setReadTimeout(timeout);
    return RestClient.builder().baseUrl(baseUrl).requestFactory(requestFactory).build();
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }
}
