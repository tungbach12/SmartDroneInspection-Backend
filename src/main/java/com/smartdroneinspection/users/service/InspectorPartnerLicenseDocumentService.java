package com.smartdroneinspection.users.service;

import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.shared.storage.EvidenceObjectStore;
import com.smartdroneinspection.shared.storage.EvidenceUploadValidator;
import com.smartdroneinspection.shared.storage.EvidenceUploadValidator.ValidatedUpload;
import com.smartdroneinspection.users.api.dto.response.InspectorPartnerLicenseUploadResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class InspectorPartnerLicenseDocumentService {

  private static final Pattern LICENSE_KEY =
      Pattern.compile(
          "inspector-partner-applications/licenses/[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\.(pdf|png|jpg|webp)");

  private final Optional<EvidenceObjectStore> objectStore;

  public InspectorPartnerLicenseDocumentService(Optional<EvidenceObjectStore> objectStore) {
    this.objectStore = objectStore;
  }

  public InspectorPartnerLicenseUploadResponse upload(MultipartFile file) {
    ValidatedUpload upload = EvidenceUploadValidator.validate(file);
    String objectKey =
        "inspector-partner-applications/licenses/" + UUID.randomUUID() + upload.extension();
    try {
      requireObjectStore()
          .put(
              objectKey,
              upload.contentType(),
              upload.content().length,
              new ByteArrayInputStream(upload.content()));
    } catch (IOException ex) {
      throw storageUnavailable();
    }
    return new InspectorPartnerLicenseUploadResponse(objectKey);
  }

  public void validateReference(String objectKey) {
    if (objectKey == null || !LICENSE_KEY.matcher(objectKey).matches()) {
      throw invalidReference();
    }
    try (InputStream ignored = requireObjectStore().open(objectKey)) {
      // Confirm the server-generated key refers to an object already uploaded.
    } catch (IOException ex) {
      throw invalidReference();
    }
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

  private BusinessException invalidReference() {
    return new BusinessException(
        HttpStatus.BAD_REQUEST,
        "INVALID_EVIDENCE_REFERENCE",
        "Pilot license document was not uploaded or is no longer available");
  }

  private BusinessException storageUnavailable() {
    return new BusinessException(
        HttpStatus.BAD_GATEWAY, "STORAGE_UNAVAILABLE", "Object storage is unavailable");
  }

  public record InputStreamContent(String contentType, InputStream stream) {}
}
