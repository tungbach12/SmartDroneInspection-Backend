package com.smartdroneinspection.inspections.service;

import com.smartdroneinspection.inspections.api.dto.response.EvidenceResponse;
import com.smartdroneinspection.inspections.domain.Evidence;
import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.domain.enums.EvidenceKind;
import com.smartdroneinspection.inspections.domain.enums.EvidenceSource;
import com.smartdroneinspection.inspections.repository.EvidenceRepository;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.shared.storage.EvidenceObjectStore;
import com.smartdroneinspection.users.UserAccess;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * MF3-01/02: evidence intake. Technical validation happens here; the substantive adequacy decision
 * does not. Bytes go to object storage before the metadata transaction commits, so a storage
 * failure never leaves an available record behind.
 */
@Service
public class EvidenceService {

  static final long MAX_SIZE_BYTES = 50L * 1024 * 1024;
  private static final Set<String> ALLOWED_TYPES =
      Set.of("image/png", "image/jpeg", "image/webp", "image/tiff", "video/mp4", "application/pdf");

  private final InspectionRepository inspections;
  private final EvidenceRepository evidence;
  private final Optional<EvidenceObjectStore> objectStore;
  private final UserAccess userAccess;

  public EvidenceService(
      InspectionRepository inspections,
      EvidenceRepository evidence,
      Optional<EvidenceObjectStore> objectStore,
      UserAccess userAccess) {
    this.inspections = inspections;
    this.evidence = evidence;
    this.objectStore = objectStore;
    this.userAccess = userAccess;
  }

  @Transactional
  public EvidenceResponse upload(
      UUID actorId,
      UUID inspectionId,
      UUID fieldSessionId,
      MultipartFile file,
      EvidenceSource source,
      Instant captureTime,
      BigDecimal latitude,
      BigDecimal longitude,
      String externalReference) {

    Inspection inspection = requireAssignedInspection(actorId, inspectionId);
    String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase();
    if (!ALLOWED_TYPES.contains(contentType)) {
      throw new BusinessException(
          HttpStatus.UNSUPPORTED_MEDIA_TYPE,
          "EVIDENCE_INVALID",
          "Unsupported evidence type. Allowed: png, jpeg, webp, tiff, mp4, pdf");
    }
    byte[] content;
    try {
      content = file.getBytes();
    } catch (IOException ex) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "EVIDENCE_INVALID", "The uploaded file could not be read");
    }
    if (content.length == 0) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "EVIDENCE_INVALID", "The uploaded file is empty");
    }
    if (content.length > MAX_SIZE_BYTES) {
      throw new BusinessException(
          HttpStatus.PAYLOAD_TOO_LARGE, "EVIDENCE_TOO_LARGE", "Maximum evidence size is 50 MB");
    }
    // Missing GPS is acceptable; a half-specified pair is not.
    if ((latitude == null) != (longitude == null)) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "EVIDENCE_INVALID",
          "Latitude and longitude must be supplied together");
    }
    if (latitude != null
        && (latitude.compareTo(BigDecimal.valueOf(-90)) < 0
            || latitude.compareTo(BigDecimal.valueOf(90)) > 0)) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "EVIDENCE_INVALID", "Latitude must be between -90 and 90");
    }
    if (longitude != null
        && (longitude.compareTo(BigDecimal.valueOf(-180)) < 0
            || longitude.compareTo(BigDecimal.valueOf(180)) > 0)) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "EVIDENCE_INVALID", "Longitude must be between -180 and 180");
    }

    String checksum = sha256(content);
    // A retry after a dropped connection must not create a second record.
    Optional<Evidence> existing =
        evidence.findByInspectionIdAndChecksumSha256(inspectionId, checksum);
    if (existing.isPresent()) {
      return toResponse(existing.get());
    }

    String objectKey = "inspections/" + inspectionId + "/evidence/" + UUID.randomUUID();
    EvidenceObjectStore store = requireObjectStore();
    try {
      store.put(objectKey, contentType, content.length, new ByteArrayInputStream(content));
    } catch (IOException ex) {
      throw new BusinessException(
          HttpStatus.BAD_GATEWAY,
          "EVIDENCE_STORAGE_UNAVAILABLE",
          "Evidence storage is unavailable");
    }

    Evidence record =
        new Evidence(
            inspection.getOrganizationId(),
            inspectionId,
            fieldSessionId,
            actorId,
            EvidenceKind.INSPECTION,
            sanitizeFileName(file.getOriginalFilename()),
            contentType,
            content.length,
            checksum,
            objectKey,
            captureTime,
            source,
            latitude,
            longitude,
            externalReference,
            null);
    return toResponse(evidence.saveAndFlush(record));
  }

  @Transactional(readOnly = true)
  public List<EvidenceResponse> list(UUID actorId, UUID inspectionId) {
    requireInspectionInScope(actorId, inspectionId);
    return evidence.findByInspectionIdOrderByCreatedAtAsc(inspectionId).stream()
        .map(this::toResponse)
        .toList();
  }

  @Transactional(readOnly = true)
  public EvidenceContent open(UUID actorId, UUID inspectionId, UUID evidenceId) {
    requireInspectionInScope(actorId, inspectionId);
    Evidence record =
        evidence
            .findByIdAndInspectionId(evidenceId, inspectionId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND, "EVIDENCE_NOT_FOUND", "Evidence not found"));
    try {
      return new EvidenceContent(
          record.getContentType(),
          record.getFileName(),
          requireObjectStore().open(record.getObjectKey()));
    } catch (IOException ex) {
      throw new BusinessException(
          HttpStatus.BAD_GATEWAY,
          "EVIDENCE_STORAGE_UNAVAILABLE",
          "Evidence storage is unavailable");
    }
  }

  /**
   * Reads evidence bytes for advisory analysis. The AI adapter is an outbound adapter, not a
   * feature.
   */
  @Transactional(readOnly = true)
  public EvidenceContent readForAnalysis(UUID inspectionId, UUID evidenceId) {
    Evidence record =
        evidence
            .findByIdAndInspectionId(evidenceId, inspectionId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND, "EVIDENCE_NOT_FOUND", "Evidence not found"));
    try {
      return new EvidenceContent(
          record.getContentType(),
          record.getFileName(),
          requireObjectStore().open(record.getObjectKey()));
    } catch (IOException ex) {
      throw new BusinessException(
          HttpStatus.BAD_GATEWAY,
          "EVIDENCE_STORAGE_UNAVAILABLE",
          "Evidence storage is unavailable");
    }
  }

  Inspection requireAssignedInspection(UUID actorId, UUID inspectionId) {
    UserAccess.ActiveUser actor = requireActiveUser(actorId);
    return inspections
        .findByIdAndOrganizationIdAndInspectorId(inspectionId, actor.organizationId(), actorId)
        .orElseThrow(this::inspectionNotFound);
  }

  Inspection requireInspectionInScope(UUID actorId, UUID inspectionId) {
    UserAccess.ActiveUser actor = requireActiveUser(actorId);
    Inspection inspection =
        inspections
            .findByIdAndOrganizationId(inspectionId, actor.organizationId())
            .orElseThrow(this::inspectionNotFound);
    // A reviewer may read the report of an inspection they did not author; the assigned Inspector
    // and
    // a qualified ORG_ADMIN both need access to the evidence behind it.
    if (!inspection.getInspectorId().equals(actorId) && !actor.hasRole("ORG_ADMIN")) {
      throw inspectionNotFound();
    }
    return inspection;
  }

  private UserAccess.ActiveUser requireActiveUser(UUID actorId) {
    UserAccess.ActiveUser actor =
        userAccess
            .findActiveUser(actorId)
            .orElseThrow(
                () ->
                    new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "User is not active"));
    if (actor.organizationId() == null) {
      throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "No organization scope");
    }
    return actor;
  }

  private BusinessException inspectionNotFound() {
    return new BusinessException(
        HttpStatus.NOT_FOUND, "INSPECTION_NOT_FOUND", "Inspection not found");
  }

  private EvidenceObjectStore requireObjectStore() {
    return objectStore.orElseThrow(
        () ->
            new BusinessException(
                HttpStatus.BAD_GATEWAY,
                "EVIDENCE_STORAGE_UNAVAILABLE",
                "Evidence storage is not configured"));
  }

  private static String sanitizeFileName(String name) {
    if (name == null || name.isBlank()) {
      return "evidence";
    }
    String cleaned = name.replaceAll("[\\\\/\\r\\n]", "_");
    return cleaned.length() > 500 ? cleaned.substring(0, 500) : cleaned;
  }

  private static String sha256(byte[] content) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    } catch (NoSuchAlgorithmException ex) {
      throw new IllegalStateException(ex);
    }
  }

  private EvidenceResponse toResponse(Evidence record) {
    return new EvidenceResponse(
        record.getId(),
        record.getInspectionId(),
        record.getFieldSessionId(),
        record.getFileName(),
        record.getContentType(),
        record.getSizeBytes(),
        record.getChecksumSha256(),
        record.getCaptureTime(),
        record.getSource(),
        record.getLatitude(),
        record.getLongitude(),
        record.getExternalReference(),
        record.getUploadStatus(),
        record.getCreatedAt());
  }

  public record EvidenceContent(String contentType, String fileName, InputStream stream) {}
}
