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

// Exercises the MinIO SDK adapter against S3Mock's S3 API subset, not MinIO server internals.
class MinioEvidenceObjectStoreIntegrationTest {

  private static final String ACCESS_KEY = "wf3-test-access";
  private static final String SECRET_KEY = "wf3-test-secret-key-123";
  private static GenericContainer<?> s3Mock;

  @BeforeAll
  static void startS3Mock() {
    s3Mock =
        new GenericContainer<>(DockerImageName.parse("adobe/s3mock:5.2.3"))
            .withExposedPorts(9090)
            .waitingFor(Wait.forHttp("/favicon.ico").forPort(9090).forStatusCode(200));
    s3Mock.start();
  }

  @AfterAll
  static void stopS3Mock() {
    if (s3Mock != null) {
      s3Mock.stop();
    }
  }

  @Test
  void createsBucketAndRoundTripsThenDeletesEvidenceBytes() throws Exception {
    MinioClient client =
        MinioClient.builder()
            .endpoint("http://" + s3Mock.getHost() + ":" + s3Mock.getMappedPort(9090))
            .credentials(ACCESS_KEY, SECRET_KEY)
            .build();
    String bucket = "wf3-test-" + UUID.randomUUID().toString().substring(0, 8);
    MinioEvidenceObjectStore store = new MinioEvidenceObjectStore(client, bucket);
    byte[] content = "s3-evidence-round-trip".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    String objectKey = "inspections/test/" + UUID.randomUUID();

    store.put(
        objectKey, "application/octet-stream", content.length, new ByteArrayInputStream(content));

    assertThat(store.open(objectKey).readAllBytes()).containsExactly(content);
    store.delete(objectKey);
    assertThatThrownBy(() -> store.open(objectKey).readAllBytes()).isInstanceOf(IOException.class);
  }
}
