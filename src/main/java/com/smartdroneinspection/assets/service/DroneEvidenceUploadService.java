package com.smartdroneinspection.assets.service;

import com.smartdroneinspection.assets.api.dto.response.DroneEvidenceUploadResponse;
import com.smartdroneinspection.assets.repository.DroneDocumentConfigRepository;
import com.smartdroneinspection.shared.exception.AuthException;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.shared.storage.EvidenceObjectStore;
import com.smartdroneinspection.shared.storage.EvidenceUploadValidator;
import com.smartdroneinspection.shared.storage.EvidenceUploadValidator.ValidatedUpload;
import com.smartdroneinspection.users.UserAccess;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DroneEvidenceUploadService {

  private static final Set<String> DEFAULT_TYPES =
      Set.of("REGISTRATION", "OWNERSHIP", "INSPECTION", "DRONE_IMAGE");
  private static final Pattern TYPE_PATTERN = Pattern.compile("^[A-Za-z0-9_]{1,64}$");
  private static final Pattern OBJECT_KEY =
      Pattern.compile(
          "drone-registration-applications/([0-9a-fA-F-]{36})/evidence/([A-Za-z0-9_]{1,64})/([0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\.(pdf|png|jpg|webp))");

  private final Optional<EvidenceObjectStore> objectStore;
  private final UserAccess userAccess;
  private final DroneDocumentConfigRepository documentConfigs;

  public DroneEvidenceUploadService(
      Optional<EvidenceObjectStore> objectStore,
      UserAccess userAccess,
      DroneDocumentConfigRepository documentConfigs) {
    this.objectStore = objectStore;
    this.userAccess = userAccess;
    this.documentConfigs = documentConfigs;
  }

  public DroneEvidenceUploadResponse upload(UUID inspectorId, String type, MultipartFile file) {
    requireInspector(inspectorId);
    if (type == null || !TYPE_PATTERN.matcher(type).matches()) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Invalid evidence type code");
    }
    String normalizedType = type.trim().toUpperCase(Locale.ROOT);
    if (!DEFAULT_TYPES.contains(normalizedType)
        && !documentConfigs.existsByCodeIgnoreCase(normalizedType)) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "VALIDATION_FAILED",
          "Unknown or unconfigured evidence type: " + type);
    }
    ValidatedUpload upload = EvidenceUploadValidator.validate(file);
    String objectKey =
        "drone-registration-applications/"
            + inspectorId
            + "/evidence/"
            + normalizedType
            + "/"
            + UUID.randomUUID()
            + upload.extension();
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
    return new DroneEvidenceUploadResponse(objectKey);
  }

  public void validateReference(UUID inspectorId, String type, String objectKey) {
    if (!isOwnedKey(inspectorId, type, objectKey)) {
      throw invalidReference();
    }
    try (InputStream ignored = requireObjectStore().open(objectKey)) {
      // Confirm this Inspector already uploaded the referenced object.
    } catch (IOException ex) {
      throw invalidReference();
    }
  }

  public EvidenceContent open(UUID reviewerId, String objectKey) {
    Matcher matcher = objectKey == null ? null : OBJECT_KEY.matcher(objectKey);
    if (matcher == null || !matcher.matches()) {
      throw new BusinessException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Evidence document not found");
    }
    requireReviewer(reviewerId);
    String extension = matcher.group(3).substring(matcher.group(3).lastIndexOf('.') + 1);
    try {
      return new EvidenceContent(
          EvidenceUploadValidator.contentType(extension), requireObjectStore().open(objectKey));
    } catch (IOException ex) {
      throw storageUnavailable();
    }
  }

  public static boolean isEvidenceKey(String objectKey) {
    return objectKey != null && OBJECT_KEY.matcher(objectKey).matches();
  }

  public static boolean isOwnedKey(UUID inspectorId, String type, String objectKey) {
    if (inspectorId == null || type == null || objectKey == null) {
      return false;
    }
    Matcher matcher = OBJECT_KEY.matcher(objectKey);
    return matcher.matches()
        && inspectorId.toString().equalsIgnoreCase(matcher.group(1))
        && type.equalsIgnoreCase(matcher.group(2));
  }

  private void requireInspector(UUID inspectorId) {
    UserAccess.ActiveUser actor =
        userAccess
            .findActiveUser(inspectorId)
            .orElseThrow(
                () ->
                    new AuthException(
                        HttpStatus.FORBIDDEN,
                        "INSPECTOR_PARTNER_REQUIRED",
                        "An active Inspector partner is required."));
    if (!actor.isInspectorPartner()) {
      throw new AuthException(
          HttpStatus.FORBIDDEN,
          "INSPECTOR_PARTNER_REQUIRED",
          "An active Inspector partner is required.");
    }
  }

  private void requireReviewer(UUID reviewerId) {
    UserAccess.ActiveUser admin =
        userAccess
            .findActiveUser(reviewerId)
            .orElseThrow(
                () ->
                    new AuthException(
                        HttpStatus.FORBIDDEN,
                        "REVIEWER_REQUIRED",
                        "Platform Reviewer access is required."));
    if (!"PLATFORM".equals(admin.actorZone())
        || (!admin.hasRole("ADMIN") && !admin.hasRole("MANAGER"))) {
      throw new AuthException(
          HttpStatus.FORBIDDEN, "REVIEWER_REQUIRED", "Platform Reviewer access is required.");
    }
  }

  private EvidenceObjectStore requireObjectStore() {
    return objectStore.orElseThrow(this::storageUnavailable);
  }

  private BusinessException storageUnavailable() {
    return new BusinessException(
        HttpStatus.BAD_GATEWAY, "STORAGE_UNAVAILABLE", "Object storage is unavailable");
  }

  private static BusinessException invalidReference() {
    return new BusinessException(
        HttpStatus.BAD_REQUEST,
        "INVALID_EVIDENCE_REFERENCE",
        "Drone evidence was not uploaded or is no longer available.");
  }

  public record EvidenceContent(String contentType, InputStream stream) {}
}
