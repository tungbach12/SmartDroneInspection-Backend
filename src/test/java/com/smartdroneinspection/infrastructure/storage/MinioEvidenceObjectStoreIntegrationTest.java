package com.smartdroneinspection.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.minio.MinioClient;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

class MinioEvidenceObjectStoreIntegrationTest {

  private static final String ACCESS_KEY = "wf3-test-access";
  private static final String SECRET_KEY = "wf3-test-secret-key-123";
  private static GenericContainer<?> minio;

  @BeforeAll
  static void startMinio() {
    minio =
        new GenericContainer<>(DockerImageName.parse("quay.io/minio/minio:latest"))
            .withEnv("MINIO_ROOT_USER", ACCESS_KEY)
            .withEnv("MINIO_ROOT_PASSWORD", SECRET_KEY)
            .withCommand("server", "/data", "--console-address", ":9001")
            .withExposedPorts(9000)
            .waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000).forStatusCode(200));
    minio.start();
  }

  @AfterAll
  static void stopMinio() {
    if (minio != null) {
      minio.stop();
    }
  }

  @Test
  void createsBucketAndRoundTripsThenDeletesEvidenceBytes() throws Exception {
    MinioClient client =
        MinioClient.builder()
            .endpoint("http://" + minio.getHost() + ":" + minio.getMappedPort(9000))
            .credentials(ACCESS_KEY, SECRET_KEY)
            .build();
    String bucket = "wf3-test-" + UUID.randomUUID().toString().substring(0, 8);
    MinioEvidenceObjectStore store = new MinioEvidenceObjectStore(client, bucket);
    byte[] content = "minio-evidence-round-trip".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    String objectKey = "inspections/test/" + UUID.randomUUID();

    store.put(
        objectKey, "application/octet-stream", content.length, new ByteArrayInputStream(content));

    assertThat(store.open(objectKey).readAllBytes()).containsExactly(content);
    store.delete(objectKey);
    assertThatThrownBy(() -> store.open(objectKey).readAllBytes()).isInstanceOf(IOException.class);
  }
}
