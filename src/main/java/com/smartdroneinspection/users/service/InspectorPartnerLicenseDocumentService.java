package com.smartdroneinspection.users.service;

import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.shared.storage.EvidenceObjectStore;
import com.smartdroneinspection.shared.storage.EvidenceUploadValidator;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class InspectorPartnerLicenseDocumentService {

  private static final Pattern LICENSE_KEY =
      Pattern.compile(
          "inspector-partner-applications/licenses/[A-Za-z0-9][A-Za-z0-9_-]{0,200}\\.(pdf|png|jpg|webp)");

  private final Optional<EvidenceObjectStore> objectStore;

  public InspectorPartnerLicenseDocumentService(Optional<EvidenceObjectStore> objectStore) {
    this.objectStore = objectStore;
  }

  public InputStreamContent open(String objectKey) {
    if (objectKey == null || !LICENSE_KEY.matcher(objectKey).matches()) {
      throw new BusinessException(HttpStatus.NOT_FOUND, "NOT_FOUND", "License document not found");
    }
    String extension = objectKey.substring(objectKey.lastIndexOf('.') + 1);
    try {
      return new InputStreamContent(
          EvidenceUploadValidator.contentType(extension), requireObjectStore().open(objectKey));
    } catch (IOException ex) {
      throw storageUnavailable();
    }
  }

  private EvidenceObjectStore requireObjectStore() {
    return objectStore.orElseThrow(this::storageUnavailable);
  }

  private BusinessException storageUnavailable() {
    return new BusinessException(
        HttpStatus.BAD_GATEWAY, "STORAGE_UNAVAILABLE", "Object storage is unavailable");
  }

  public record InputStreamContent(String contentType, InputStream stream) {}
}
