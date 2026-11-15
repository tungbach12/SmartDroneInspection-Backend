package com.smartdroneinspection.shared.storage;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/** Storage boundary for binary objects shared by feature modules. */
public interface EvidenceObjectStore {

  void put(String objectKey, String contentType, long sizeBytes, InputStream input)
      throws IOException;

  InputStream open(String objectKey) throws IOException;

  default InputStream openRange(String objectKey, long offset, long length) throws IOException {
    throw new UnsupportedOperationException("Ranged object reads are not supported by this store");
  }

  default ObjectMetadata stat(String objectKey) throws IOException {
    throw new UnsupportedOperationException("Object metadata stat is not supported by this store");
  }

  default String initiateMultipartUpload(String objectKey, String contentType) throws IOException {
    throw new UnsupportedOperationException("Multipart upload is not supported by this store");
  }

  default String uploadPart(
      String objectKey, String uploadId, int partNumber, long sizeBytes, InputStream input)
      throws IOException {
    throw new UnsupportedOperationException("Multipart upload is not supported by this store");
  }

  default List<UploadedPart> listUploadedParts(String objectKey, String uploadId)
      throws IOException {
    throw new UnsupportedOperationException("Multipart upload is not supported by this store");
  }

  default void completeMultipartUpload(String objectKey, String uploadId, List<UploadedPart> parts)
      throws IOException {
    throw new UnsupportedOperationException("Multipart upload is not supported by this store");
  }

  default void abortMultipartUpload(String objectKey, String uploadId) throws IOException {
    throw new UnsupportedOperationException("Multipart upload is not supported by this store");
  }

  void delete(String objectKey) throws IOException;

  record ObjectMetadata(long sizeBytes, String contentType, String etag) {}

  record UploadedPart(int partNumber, String etag, long sizeBytes) {}
}
