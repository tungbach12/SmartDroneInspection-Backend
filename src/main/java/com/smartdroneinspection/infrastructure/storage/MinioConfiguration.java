package com.smartdroneinspection.infrastructure.storage;

import com.smartdroneinspection.shared.storage.EvidenceObjectStore;
import io.minio.MinioAsyncClient;
import io.minio.MinioClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MinioProperties.class)
@ConditionalOnProperty(prefix = "app.storage.minio", name = "enabled", havingValue = "true")
class MinioConfiguration {

  @Bean
  MinioClient minioClient(MinioProperties properties) {
    validateProperties(properties);
    return MinioClient.builder()
        .endpoint(properties.endpoint())
        .credentials(properties.accessKey(), properties.secretKey())
        .build();
  }

  @Bean
  MinioAsyncClient minioAsyncClient(MinioProperties properties) {
    validateProperties(properties);
    return MinioAsyncClient.builder()
        .endpoint(properties.endpoint())
        .credentials(properties.accessKey(), properties.secretKey())
        .build();
  }

  @Bean
  EvidenceObjectStore evidenceObjectStore(
      MinioClient client, MinioAsyncClient asyncClient, MinioProperties properties) {
    return new MinioEvidenceObjectStore(client, asyncClient, properties.bucket());
  }

  private static void validateProperties(MinioProperties properties) {
    if (isBlank(properties.endpoint())
        || isBlank(properties.bucket())
        || isBlank(properties.accessKey())
        || isBlank(properties.secretKey())) {
      throw new IllegalStateException(
          "MinIO endpoint, bucket, and credentials must be configured.");
    }
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }
}
