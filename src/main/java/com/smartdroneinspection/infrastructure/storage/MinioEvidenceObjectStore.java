package com.smartdroneinspection.infrastructure.storage;

import com.smartdroneinspection.shared.storage.EvidenceObjectStore;
import io.minio.AbortMultipartUploadArgs;
import io.minio.BucketExistsArgs;
import io.minio.CompleteMultipartUploadArgs;
import io.minio.CreateMultipartUploadArgs;
import io.minio.GetObjectArgs;
import io.minio.Http;
import io.minio.ListPartsArgs;
import io.minio.ListPartsResponse;
import io.minio.MakeBucketArgs;
import io.minio.MinioAsyncClient;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.UploadPartArgs;
import io.minio.messages.Part;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

final class MinioEvidenceObjectStore implements EvidenceObjectStore {

  private final MinioClient client;
  private final MinioAsyncClient asyncClient;
  private final String bucket;

  MinioEvidenceObjectStore(MinioClient client, String bucket) {
    this(client, null, bucket);
  }

  MinioEvidenceObjectStore(MinioClient client, MinioAsyncClient asyncClient, String bucket) {
    this.client = client;
    this.asyncClient = asyncClient;
    this.bucket = bucket;
  }

  @Override
  public void put(String objectKey, String contentType, long sizeBytes, InputStream input)
      throws IOException {
    try {
      ensureBucket();
      client.putObject(
          PutObjectArgs.builder().bucket(bucket).object(objectKey).stream(input, sizeBytes, -1L)
              .contentType(contentType)
              .build());
    } catch (Exception exception) {
      throw storageFailure(exception);
    }
  }

  @Override
  public InputStream open(String objectKey) throws IOException {
    try {
      return client.getObject(GetObjectArgs.builder().bucket(bucket).object(objectKey).build());
    } catch (Exception exception) {
      throw storageFailure(exception);
    }
  }

  @Override
  public InputStream openRange(String objectKey, long offset, long length) throws IOException {
    if (offset < 0 || length < 0) {
      throw new IllegalArgumentException("Range offset and length must be non-negative");
    }
    try {
      return client.getObject(
          GetObjectArgs.builder()
              .bucket(bucket)
              .object(objectKey)
              .offset(offset)
              .length(length)
              .build());
    } catch (Exception exception) {
      throw storageFailure(exception);
    }
  }

  @Override
  public ObjectMetadata stat(String objectKey) throws IOException {
    try {
      StatObjectResponse response =
          client.statObject(StatObjectArgs.builder().bucket(bucket).object(objectKey).build());
      return new ObjectMetadata(response.size(), response.contentType(), response.etag());
    } catch (Exception exception) {
      throw storageFailure(exception);
    }
  }

  @Override
  public String initiateMultipartUpload(String objectKey, String contentType) throws IOException {
    MinioAsyncClient multipart = requireAsyncClient();
    try {
      ensureBucket();
      Http.Headers headers =
          contentType != null && !contentType.isBlank()
              ? new Http.Headers(Map.of("Content-Type", contentType))
              : null;
      CreateMultipartUploadArgs.Builder builder =
          CreateMultipartUploadArgs.builder().bucket(bucket).object(objectKey);
      if (headers != null) {
        builder.headers(headers);
      }
      var response = multipart.createMultipartUpload(builder.build()).get();
      return response.result().uploadId();
    } catch (Exception exception) {
      throw storageFailure(exception);
    }
  }

  @Override
  public String uploadPart(
      String objectKey, String uploadId, int partNumber, long sizeBytes, InputStream input)
      throws IOException {
    if (sizeBytes <= 0 || sizeBytes > Integer.MAX_VALUE) {
      throw new IllegalArgumentException("Invalid part size: " + sizeBytes);
    }
    MinioAsyncClient multipart = requireAsyncClient();
    try {
      byte[] bytes = input.readNBytes((int) sizeBytes);
      if (bytes.length != sizeBytes || input.read() != -1) {
        throw new IOException(
            "Part size mismatch: expected " + sizeBytes + " bytes but received " + bytes.length);
      }
      var response =
          multipart
              .uploadPart(
                  UploadPartArgs.builder()
                      .bucket(bucket)
                      .object(objectKey)
                      .uploadId(uploadId)
                      .partNumber(partNumber)
                      .data(bytes, bytes.length)
                      .build())
              .get();
      return response.part().etag();
    } catch (IOException exception) {
      throw exception;
    } catch (Exception exception) {
      throw storageFailure(exception);
    }
  }

  @Override
  public List<UploadedPart> listUploadedParts(String objectKey, String uploadId)
      throws IOException {
    MinioAsyncClient multipart = requireAsyncClient();
    try {
      List<UploadedPart> parts = new ArrayList<>();
      Integer marker = null;
      while (true) {
        ListPartsArgs baseArgs =
            ListPartsArgs.builder()
                .bucket(bucket)
                .uploadId(uploadId)
                .maxParts(1000)
                .partNumberMarker(marker)
                .build();
        ListPartsArgs args = new ObjectListPartsArgs(baseArgs, objectKey);
        ListPartsResponse response = multipart.listParts(args).get();
        List<Part> page = response.result().parts();
        if (page != null) {
          for (Part part : page) {
            parts.add(new UploadedPart(part.partNumber(), part.etag(), part.partSize()));
          }
        }
        if (!response.result().isTruncated()) {
          break;
        }
        marker = response.result().nextPartNumberMarker();
      }
      parts.sort(Comparator.comparingInt(UploadedPart::partNumber));
      return parts;
    } catch (Exception exception) {
      throw storageFailure(exception);
    }
  }

  @Override
  public void completeMultipartUpload(String objectKey, String uploadId, List<UploadedPart> parts)
      throws IOException {
    MinioAsyncClient multipart = requireAsyncClient();
    try {
      Part[] sdkParts =
          parts.stream()
              .sorted(Comparator.comparingInt(UploadedPart::partNumber))
              .map(part -> new Part(part.partNumber(), part.etag()))
              .toArray(Part[]::new);
      multipart
          .completeMultipartUpload(
              CompleteMultipartUploadArgs.builder()
                  .bucket(bucket)
                  .object(objectKey)
                  .uploadId(uploadId)
                  .parts(sdkParts)
                  .build())
          .get();
    } catch (Exception exception) {
      throw storageFailure(exception);
    }
  }

  @Override
  public void abortMultipartUpload(String objectKey, String uploadId) throws IOException {
    MinioAsyncClient multipart = requireAsyncClient();
    try {
      multipart
          .abortMultipartUpload(
              AbortMultipartUploadArgs.builder()
                  .bucket(bucket)
                  .object(objectKey)
                  .uploadId(uploadId)
                  .build())
          .get();
    } catch (Exception exception) {
      throw storageFailure(exception);
    }
  }

  @Override
  public void delete(String objectKey) throws IOException {
    try {
      client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build());
    } catch (Exception exception) {
      throw storageFailure(exception);
    }
  }

  private MinioAsyncClient requireAsyncClient() {
    if (asyncClient == null) {
      throw new IllegalStateException("MinioAsyncClient is required for multipart operations");
    }
    return asyncClient;
  }

  private void ensureBucket() throws Exception {
    if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
      try {
        client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
      } catch (io.minio.errors.ErrorResponseException exception) {
        if (!"BucketAlreadyOwnedByYou".equals(exception.errorResponse().code())) {
          throw exception;
        }
      }
    }
  }

  private IOException storageFailure(Exception exception) {
    return new IOException("The evidence object-store operation failed.", exception);
  }

  /**
   * MinIO SDK 9.0.3's {@link ListPartsArgs.Builder} extends {@code BucketArgs.Builder} rather than
   * {@code ObjectArgs.Builder}, omitting {@code object(...)}. This adapter supplies {@code
   * objectName} on the underlying {@code ObjectArgs} field.
   */
  private static final class ObjectListPartsArgs extends ListPartsArgs {
    private final ListPartsArgs delegate;

    private ObjectListPartsArgs(ListPartsArgs delegate, String objectName) {
      this.delegate = delegate;
      this.bucketName = delegate.bucket();
      this.region = delegate.region();
      this.objectName = objectName;
    }

    @Override
    public String uploadId() {
      return delegate.uploadId();
    }

    @Override
    public Integer maxParts() {
      return delegate.maxParts();
    }

    @Override
    public Integer partNumberMarker() {
      return delegate.partNumberMarker();
    }
  }
}
