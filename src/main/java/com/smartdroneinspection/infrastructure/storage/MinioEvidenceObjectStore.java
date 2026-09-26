package com.smartdroneinspection.infrastructure.storage;

import com.smartdroneinspection.shared.storage.EvidenceObjectStore;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import java.io.IOException;
import java.io.InputStream;

final class MinioEvidenceObjectStore implements EvidenceObjectStore {

  private final MinioClient client;
  private final String bucket;

  MinioEvidenceObjectStore(MinioClient client, String bucket) {
    this.client = client;
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
  public void delete(String objectKey) throws IOException {
    try {
      client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build());
    } catch (Exception exception) {
      throw storageFailure(exception);
    }
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
}
