package com.smartdroneinspection.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartdroneinspection.inspections.spi.EvidenceObjectStore;
import io.minio.MinioClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class MinioConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner().withUserConfiguration(MinioConfiguration.class);

  @Test
  void isDisabledByDefault() {
    contextRunner.run(context -> assertThat(context).doesNotHaveBean(EvidenceObjectStore.class));
  }

  @Test
  void createsClientAndEvidenceStoreWhenConfigured() {
    contextRunner
        .withPropertyValues(
            "app.storage.minio.enabled=true",
            "app.storage.minio.endpoint=http://localhost:9000",
            "app.storage.minio.bucket=test-evidence",
            "app.storage.minio.access-key=test-access",
            "app.storage.minio.secret-key=test-secret")
        .run(
            context -> {
              assertThat(context).hasSingleBean(MinioClient.class);
              assertThat(context).hasSingleBean(EvidenceObjectStore.class);
            });
  }

  @Test
  void rejectsEnabledStorageWithoutCredentials() {
    contextRunner
        .withPropertyValues(
            "app.storage.minio.enabled=true",
            "app.storage.minio.endpoint=http://localhost:9000",
            "app.storage.minio.bucket=test-evidence")
        .run(
            context -> {
              assertThat(context).hasFailed();
              assertThat(context.getStartupFailure())
                  .hasRootCauseInstanceOf(IllegalStateException.class)
                  .hasRootCauseMessage(
                      "MinIO endpoint, bucket, and credentials must be configured.");
            });
  }
}
