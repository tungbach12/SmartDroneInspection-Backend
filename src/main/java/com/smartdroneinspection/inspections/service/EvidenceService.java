package com.smartdroneinspection.inspections.service;

import com.smartdroneinspection.inspectionrequests.domain.InspectionAssignment;
import com.smartdroneinspection.inspectionrequests.domain.enums.InspectionAssignmentStatus;
import com.smartdroneinspection.inspectionrequests.repository.InspectionAssignmentRepository;
import com.smartdroneinspection.inspections.api.dto.request.EvidenceUploadMetadataRequest;
import com.smartdroneinspection.inspections.api.dto.response.EvidenceResponse;
import com.smartdroneinspection.inspections.domain.Evidence;
import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.domain.enums.EvidenceKind;
import com.smartdroneinspection.inspections.domain.enums.InspectionStatus;
import com.smartdroneinspection.inspections.domain.enums.UploadStatus;
import com.smartdroneinspection.inspections.repository.EvidenceRepository;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.shared.auth.Roles;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.shared.storage.EvidenceObjectStore;
import com.smartdroneinspection.users.UserAccess;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

@Service
public class EvidenceService {

  private static final Logger LOG = LoggerFactory.getLogger(EvidenceService.class);
  private static final int MAX_FILE_NAME_LENGTH = 500;

  private final InspectionRepository inspections;
  private final InspectionAssignmentRepository assignments;
  private final EvidenceRepository evidence;
  private final UserAccess users;
  private final Optional<EvidenceObjectStore> objectStore;
  private final long maxFileSizeBytes;

  public EvidenceService(
      InspectionRepository inspections,
      InspectionAssignmentRepository assignments,
      EvidenceRepository evidence,
      UserAccess users,
      Optional<EvidenceObjectStore> objectStore,
      @Value("${app.evidence.max-file-size:1MB}") DataSize maxFileSize) {
    this.inspections = inspections;
    this.assignments = assignments;
    this.evidence = evidence;
    this.users = users;
    this.objectStore = objectStore;
    this.maxFileSizeBytes = maxFileSize.toBytes();
  }

  @Transactional
  public EvidenceResponse upload(
      UUID actorId, UUID inspectionId, MultipartFile file, EvidenceUploadMetadataRequest metadata) {
    Inspection inspection = requireAssignedInspection(actorId, inspectionId, true);
    validateMetadata(metadata);
    byte[] content = readAndValidate(file);
    String checksum = sha256(content);
    Optional<Evidence> existing =
        evidence.findByInspectionIdAndChecksumSha256(inspectionId, checksum);
    if (existing.isPresent()) {
      Evidence duplicate = existing.get();
      if (duplicate.getUploadStatus() != UploadStatus.AVAILABLE) {
        throw stateConflict("Evidence with this checksum is not currently available.");
      }
      return toResponse(duplicate);
    }

    EvidenceObjectStore store =
        objectStore.orElseThrow(
            () ->
                new BusinessException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "EVIDENCE_STORAGE_UNAVAILABLE",
                    "Evidence storage is not configured."));
    UUID objectId = UUID.randomUUID();
    String objectKey = "inspections/" + inspectionId + "/" + objectId;
    registerRollbackCleanup(store, objectKey);
    try (InputStream input = new ByteArrayInputStream(content)) {
      store.put(objectKey, detectContentType(content), content.length, input);
    } catch (IOException exception) {
      deleteQuietly(store, objectKey);
      throw new BusinessException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "EVIDENCE_STORAGE_UNAVAILABLE",
          "Evidence could not be stored. Retry the upload.");
    }

    Evidence saved =
        evidence.saveAndFlush(
            new Evidence(
                inspection.getId(),
                null,
                actorId,
                EvidenceKind.INSPECTION,
                sanitizeFileName(file.getOriginalFilename()),
                detectContentType(content),
                content.length,
                checksum,
                objectKey,
                metadata.captureTime(),
                metadata.source(),
                metadata.latitude(),
                metadata.longitude(),
                metadata.externalReference(),
                UploadStatus.AVAILABLE));
    return toResponse(saved);
  }

  @Transactional(readOnly = true)
  public List<EvidenceResponse> list(UUID actorId, UUID inspectionId) {
    requireAssignedInspection(actorId, inspectionId, false);
    return evidence
        .findByInspectionIdAndUploadStatusOrderByCreatedAtDesc(inspectionId, UploadStatus.AVAILABLE)
        .stream()
        .map(this::toResponse)
        .toList();
  }

  @Transactional(readOnly = true)
  public EvidenceContent open(UUID actorId, UUID inspectionId, UUID evidenceId) {
    requireAssignedInspection(actorId, inspectionId, false);
    Evidence record =
        evidence
            .findByIdAndInspectionIdAndUploadStatus(
                evidenceId, inspectionId, UploadStatus.AVAILABLE)
            .orElseThrow(this::evidenceNotFound);
    EvidenceObjectStore store =
        objectStore.orElseThrow(
            () ->
                new BusinessException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "EVIDENCE_STORAGE_UNAVAILABLE",
                    "Evidence storage is not configured."));
    try {
      return new EvidenceContent(
          store.open(record.getObjectKey()),
          record.getFileName(),
          record.getContentType(),
          record.getSizeBytes());
    } catch (IOException exception) {
      throw new BusinessException(
          HttpStatus.SERVICE_UNAVAILABLE,
          "EVIDENCE_STORAGE_UNAVAILABLE",
          "Evidence content is temporarily unavailable.");
    }
  }

  private Inspection requireAssignedInspection(
      UUID actorId, UUID inspectionId, boolean lockForUpdate) {
    UserAccess.ActiveUser actor =
        users
            .findActiveUser(actorId)
            .filter(user -> user.hasRole(Roles.INSPECTOR))
            .orElseThrow(this::scopeDenied);
    if (!actor.hasRole(Roles.INSPECTOR)) {
      throw scopeDenied();
    }
    Inspection inspection =
        (lockForUpdate
                ? inspections.findForUpdateByIdAndAuthorUserId(inspectionId, actorId)
                : inspections.findByIdAndAuthorUserId(inspectionId, actorId))
            .orElseThrow(this::scopeDenied);
    InspectionAssignment assignment =
        assignments
            .findByIdAndInspectorUserId(inspection.getAcceptedAssignmentId(), actorId)
            .filter(value -> value.getStatus() == InspectionAssignmentStatus.ACCEPTED)
            .orElseThrow(this::scopeDenied);
    if (!inspection.getAuthorUserId().equals(actor.id())
        || !assignment.getInspectorUserId().equals(actorId)) {
      throw scopeDenied();
    }
    if (lockForUpdate && inspection.getStatus() != InspectionStatus.IN_PROGRESS) {
      throw stateConflict("Evidence can only be added to an in-progress inspection.");
    }
    return inspection;
  }

  private void validateMetadata(EvidenceUploadMetadataRequest metadata) {
    if (metadata == null || metadata.source() == null) {
      throw invalidEvidence("Evidence source is required.");
    }
    if ((metadata.latitude() == null) != (metadata.longitude() == null)) {
      throw invalidEvidence("Latitude and longitude must be provided together.");
    }
    if (metadata.latitude() != null
        && (metadata.latitude().compareTo(BigDecimal.valueOf(-90)) < 0
            || metadata.latitude().compareTo(BigDecimal.valueOf(90)) > 0
            || metadata.longitude().compareTo(BigDecimal.valueOf(-180)) < 0
            || metadata.longitude().compareTo(BigDecimal.valueOf(180)) > 0)) {
      throw invalidEvidence("GPS coordinates are outside the valid range.");
    }
  }

  private byte[] readAndValidate(MultipartFile file) {
    if (file == null || file.isEmpty()) {
      throw invalidEvidence("Evidence file is required.");
    }
    if (file.getSize() > maxFileSizeBytes) {
      throw new BusinessException(
          HttpStatus.CONTENT_TOO_LARGE,
          "EVIDENCE_TOO_LARGE",
          "Evidence file exceeds the configured size limit.");
    }
    try {
      byte[] content = file.getBytes();
      String detectedType = detectContentType(content);
      if (detectedType == null
          || (file.getContentType() != null
              && !file.getContentType().isBlank()
              && !"application/octet-stream".equalsIgnoreCase(file.getContentType())
              && !detectedType.equalsIgnoreCase(file.getContentType()))) {
        throw invalidEvidence("Evidence content is unsupported or corrupt.");
      }
      return content;
    } catch (IOException exception) {
      throw invalidEvidence("Evidence content could not be read.");
    }
  }

  private String detectContentType(byte[] content) {
    try {
      if (startsWith(content, new byte[] {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10})
          && ImageIO.read(new ByteArrayInputStream(content)) != null) {
        return "image/png";
      }
      if (startsWith(content, new byte[] {(byte) 0xff, (byte) 0xd8, (byte) 0xff})
          && ImageIO.read(new ByteArrayInputStream(content)) != null) {
        return "image/jpeg";
      }
      if (startsWith(content, "%PDF-".getBytes(java.nio.charset.StandardCharsets.US_ASCII))) {
        int tailStart = Math.max(0, content.length - 2048);
        String tail =
            new String(
                content,
                tailStart,
                content.length - tailStart,
                java.nio.charset.StandardCharsets.ISO_8859_1);
        if (tail.contains("%%EOF")) {
          return "application/pdf";
        }
      }
      return null;
    } catch (IOException exception) {
      return null;
    }
  }

  private boolean startsWith(byte[] value, byte[] prefix) {
    if (value.length < prefix.length) {
      return false;
    }
    for (int index = 0; index < prefix.length; index++) {
      if (value[index] != prefix[index]) {
        return false;
      }
    }
    return true;
  }

  private String sanitizeFileName(String originalName) {
    if (originalName == null || originalName.isBlank()) {
      return "evidence";
    }
    String normalized = originalName.replace('\\', '/');
    int lastSeparator = normalized.lastIndexOf('/');
    String fileName = normalized.substring(lastSeparator + 1).replaceAll("[\\p{Cntrl}]", "_");
    if (fileName.isBlank()) {
      return "evidence";
    }
    return fileName.length() > MAX_FILE_NAME_LENGTH
        ? fileName.substring(0, MAX_FILE_NAME_LENGTH)
        : fileName;
  }

  private String sha256(byte[] content) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    } catch (java.security.NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable.", exception);
    }
  }

  private void registerRollbackCleanup(EvidenceObjectStore store, String objectKey) {
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCompletion(int status) {
            if (status != STATUS_COMMITTED) {
              deleteQuietly(store, objectKey);
            }
          }
        });
  }

  private void deleteQuietly(EvidenceObjectStore store, String objectKey) {
    try {
      store.delete(objectKey);
    } catch (IOException exception) {
      LOG.warn("Could not clean up staged inspection evidence after a failed upload.");
    }
  }

  private EvidenceResponse toResponse(Evidence record) {
    return new EvidenceResponse(
        record.getId(),
        record.getFileName(),
        record.getContentType(),
        record.getSizeBytes(),
        record.getChecksumSha256(),
        record.getSource(),
        record.getCaptureTime(),
        record.getLatitude(),
        record.getLongitude(),
        record.getExternalReference(),
        record.getUploadStatus(),
        record.getCreatedAt());
  }

  private BusinessException scopeDenied() {
    return new BusinessException(
        HttpStatus.FORBIDDEN,
        "INSPECTION_SCOPE_DENIED",
        "The inspection is not available to this Inspector.");
  }

  private BusinessException evidenceNotFound() {
    return new BusinessException(
        HttpStatus.NOT_FOUND, "EVIDENCE_NOT_FOUND", "Evidence resource was not found.");
  }

  private BusinessException stateConflict(String message) {
    return new BusinessException(HttpStatus.CONFLICT, "INSPECTION_STATE_CONFLICT", message);
  }

  private BusinessException invalidEvidence(String message) {
    return new BusinessException(HttpStatus.UNPROCESSABLE_CONTENT, "EVIDENCE_INVALID", message);
  }

  public record EvidenceContent(
      InputStream stream, String fileName, String contentType, long sizeBytes) {}
}
