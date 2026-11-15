package com.smartdroneinspection.infrastructure.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.smartdroneinspection.shared.storage.EvidenceObjectStore;
import io.minio.MinioAsyncClient;
import io.minio.MinioClient;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
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

  private MinioEvidenceObjectStore newStore(String bucket) {
    String endpoint = "http://" + s3Mock.getHost() + ":" + s3Mock.getMappedPort(9090);
    MinioClient client =
        MinioClient.builder().endpoint(endpoint).credentials(ACCESS_KEY, SECRET_KEY).build();
    MinioAsyncClient asyncClient =
        MinioAsyncClient.builder().endpoint(endpoint).credentials(ACCESS_KEY, SECRET_KEY).build();
    return new MinioEvidenceObjectStore(client, asyncClient, bucket);
  }

  @Test
  void createsBucketAndRoundTripsThenDeletesEvidenceBytes() throws Exception {
    String bucket = "wf3-test-" + UUID.randomUUID().toString().substring(0, 8);
    MinioEvidenceObjectStore store = newStore(bucket);
    byte[] content = "s3-evidence-round-trip".getBytes(StandardCharsets.UTF_8);
    String objectKey = "inspections/test/" + UUID.randomUUID();

    store.put(objectKey, "text/plain", content.length, new ByteArrayInputStream(content));

    EvidenceObjectStore.ObjectMetadata meta = store.stat(objectKey);
    assertThat(meta.sizeBytes()).isEqualTo(content.length);
    assertThat(meta.contentType()).contains("text/plain");
    assertThat(store.open(objectKey).readAllBytes()).containsExactly(content);
    try (var range = store.openRange(objectKey, 3, 8)) {
      assertThat(range.readAllBytes()).containsExactly(Arrays.copyOfRange(content, 3, 11));
    }

    store.delete(objectKey);
    assertThatThrownBy(() -> store.open(objectKey).readAllBytes()).isInstanceOf(IOException.class);
  }

  @Test
  void multipartUploadListsCompletesStatsAndSupportsRangeReadsAndAbort() throws Exception {
    String bucket = "wf3-mp-" + UUID.randomUUID().toString().substring(0, 8);
    MinioEvidenceObjectStore store = newStore(bucket);
    String objectKey = "inspections/test/" + UUID.randomUUID() + ".mp4";

    byte[] part1 = new byte[5 * 1024 * 1024 + 64];
    Arrays.fill(part1, (byte) 'A');
    part1[0] = 'S';
    part1[part1.length - 1] = 'M';
    byte[] part2 = "TAIL-BYTES-12345".getBytes(StandardCharsets.UTF_8);

    String uploadId = store.initiateMultipartUpload(objectKey, "video/mp4");
    assertThat(uploadId).isNotBlank();

    String etag1 =
        store.uploadPart(objectKey, uploadId, 1, part1.length, new ByteArrayInputStream(part1));
    String etag2 =
        store.uploadPart(objectKey, uploadId, 2, part2.length, new ByteArrayInputStream(part2));

    List<EvidenceObjectStore.UploadedPart> listed = store.listUploadedParts(objectKey, uploadId);
    assertThat(listed).hasSize(2);
    assertThat(listed.get(0).partNumber()).isEqualTo(1);
    assertThat(listed.get(1).partNumber()).isEqualTo(2);

    store.completeMultipartUpload(
        objectKey,
        uploadId,
        List.of(
            new EvidenceObjectStore.UploadedPart(1, etag1, part1.length),
            new EvidenceObjectStore.UploadedPart(2, etag2, part2.length)));

    long totalLength = (long) part1.length + part2.length;
    assertThat(store.stat(objectKey).sizeBytes()).isEqualTo(totalLength);

    try (var tail = store.openRange(objectKey, part1.length - 1L, part2.length + 1L)) {
      byte[] tailBytes = tail.readAllBytes();
      assertThat(tailBytes[0]).isEqualTo((byte) 'M');
      assertThat(Arrays.copyOfRange(tailBytes, 1, tailBytes.length)).isEqualTo(part2);
    }

    String abortedKey = "inspections/test/" + UUID.randomUUID() + ".mp4";
    String abortedUploadId = store.initiateMultipartUpload(abortedKey, "video/mp4");
    store.uploadPart(abortedKey, abortedUploadId, 1, part2.length, new ByteArrayInputStream(part2));
    store.abortMultipartUpload(abortedKey, abortedUploadId);
    assertThatThrownBy(() -> store.stat(abortedKey)).isInstanceOf(IOException.class);
  }
}
