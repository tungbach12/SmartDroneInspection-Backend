package com.smartdroneinspection.inspections.spi;

import java.io.IOException;
import java.io.InputStream;

/** Storage boundary owned by the inspection evidence use case. */
public interface EvidenceObjectStore {

  void put(String objectKey, String contentType, long sizeBytes, InputStream input)
      throws IOException;

  InputStream open(String objectKey) throws IOException;

  void delete(String objectKey) throws IOException;
}
