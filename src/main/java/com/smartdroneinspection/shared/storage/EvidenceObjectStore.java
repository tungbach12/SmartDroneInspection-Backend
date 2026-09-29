package com.smartdroneinspection.shared.storage;

import java.io.IOException;
import java.io.InputStream;

/** Storage boundary for binary objects shared by feature modules. */
public interface EvidenceObjectStore {

  void put(String objectKey, String contentType, long sizeBytes, InputStream input)
      throws IOException;

  InputStream open(String objectKey) throws IOException;

  void delete(String objectKey) throws IOException;
}
