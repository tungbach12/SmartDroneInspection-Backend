package com.smartdroneinspection.inspections.service;

import com.smartdroneinspection.inspections.api.dto.request.BeginEvidenceUploadRequest;
import com.smartdroneinspection.inspections.api.dto.response.EvidenceUploadSessionResponse;
import com.smartdroneinspection.inspections.domain.Evidence;
import com.smartdroneinspection.inspections.domain.EvidenceUploadSession;
import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.domain.enums.EvidenceProcessingStatus;
import com.smartdroneinspection.inspections.domain.enums.EvidenceUploadSessionStatus;
import com.smartdroneinspection.inspections.repository.EvidenceRepository;
import com.smartdroneinspection.inspections.repository.EvidenceUploadSessionRepository;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.shared.storage.EvidenceObjectStore;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Manages resumable, bounded-chunk uploads for large inspection videos up to 2 GiB without
 * buffering the full payload in JVM heap or exposing object storage to browsers.
 */
@Service
@EnableConfigurationProperties(EvidenceUploadProperties.class)
public class EvidenceUploadSessionService {

  private static final Logger LOG = LoggerFactory.getLogger(EvidenceUploadSessionService.class);
  private static final TypeReference<List<StoredPartRecord>> PART_LIST_TYPE =
      new TypeReference<>() {};

  private final EvidenceService evidenceService;
  private final EvidenceUploadSessionRepository sessions;
  private final EvidenceRepository evidence;
  private final Optional<EvidenceObjectStore> objectStore;
  private final EvidenceUploadProperties properties;
  private final Optional<VideoProcessingWorker> videoWorker;
  private final ObjectMapper objectMapper;

  public EvidenceUploadSessionService(
      EvidenceService evidenceService,
      EvidenceUploadSessionRepository sessions,
      EvidenceRepository evidence,
      Optional<EvidenceObjectStore> objectStore,
      EvidenceUploadProperties properties,
      Optional<VideoProcessingWorker> videoWorker,
      ObjectMapper objectMapper) {
    this.evidenceService = evidenceService;
    this.sessions = sessions;
    this.evidence = evidence;
    this.objectStore = objectStore;
    this.properties = properties;
    this.videoWorker = videoWorker;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public EvidenceUploadSessionResponse beginUpload(
      UUID actorId, UUID inspectionId, BeginEvidenceUploadRequest request) {
    Inspection inspection = evidenceService.requireAssignedInspection(actorId, inspectionId);
    evidenceService.requireEvidenceEditable(inspectionId, inspection);
    if (request.fieldSessionId() != null) {
      evidenceService.requireSessionInScope(inspection, request.fieldSessionId());
    }
    String contentType =
        request.contentType() == null ? "" : request.contentType().trim().toLowerCase(Locale.ROOT);
    if (!"video/mp4".equals(contentType)) {
      throw new BusinessException(
          HttpStatus.UNSUPPORTED_MEDIA_TYPE,
          "EVIDENCE_INVALID",
          "Resumable chunked uploads currently support video/mp4 only");
    }
    long totalSize = request.totalSizeBytes();
    if (totalSize <= 0) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "EVIDENCE_INVALID", "The uploaded file is empty");
    }
    if (totalSize > properties.getMaxVideoSizeBytes()) {
      throw new BusinessException(
          HttpStatus.PAYLOAD_TOO_LARGE,
          "EVIDENCE_TOO_LARGE",
          "Maximum video evidence size is "
              + (properties.getMaxVideoSizeBytes() / (1024 * 1024))
              + " MB");
    }
    EvidenceService.validateGpsPair(request.latitude(), request.longitude());

    String fileName = EvidenceService.sanitizeFileName(request.fileName());
    ensureNoFilenameConflict(inspectionId, fileName);

    int chunkSize = properties.getChunkSizeBytes();
    int totalParts = (int) ((totalSize + chunkSize - 1) / chunkSize);
    String rawObjectKey =
        "inspections/" + inspectionId + "/evidence/raw/" + UUID.randomUUID() + ".mp4";

    EvidenceObjectStore store = requireObjectStore();
    String uploadId;
    try {
      uploadId = store.initiateMultipartUpload(rawObjectKey, contentType);
    } catch (IOException ex) {
      throw new BusinessException(
          HttpStatus.BAD_GATEWAY,
          "EVIDENCE_STORAGE_UNAVAILABLE",
          "Evidence storage is unavailable");
    }

    Instant now = Instant.now();
    Instant expiresAt = now.plus(properties.getUploadSessionTtl());
    String normalizedLocation =
        request.locationDescription() == null || request.locationDescription().isBlank()
            ? null
            : request.locationDescription().trim();

    EvidenceUploadSession session =
        new EvidenceUploadSession(
            inspectionId,
            request.fieldSessionId(),
            actorId,
            fileName,
            contentType,
            totalSize,
            chunkSize,
            totalParts,
            uploadId,
            rawObjectKey,
            request.captureTime(),
            request.latitude(),
            request.longitude(),
            normalizedLocation,
            expiresAt,
            now);
    EvidenceUploadSession saved = sessions.saveAndFlush(session);
    return toResponse(saved);
  }

  @Transactional
  public EvidenceUploadSessionResponse getStatus(UUID actorId, UUID inspectionId, UUID sessionId) {
    evidenceService.requireAssignedInspection(actorId, inspectionId);
    EvidenceUploadSession session = requireSession(inspectionId, sessionId);
    if (expireIfNeeded(session, Instant.now())) {
      return toResponse(session);
    }
    if (session.getStatus() == EvidenceUploadSessionStatus.OPEN && objectStore.isPresent()) {
      try {
        List<EvidenceObjectStore.UploadedPart> remoteParts =
            objectStore
                .get()
                .listUploadedParts(session.getRawObjectKey(), session.getStorageUploadId());
        if (!remoteParts.isEmpty()) {
          Map<Integer, StoredPartRecord> merged = readPartsMap(session.getUploadedPartsJson());
          for (EvidenceObjectStore.UploadedPart remote : remoteParts) {
            merged.put(
                remote.partNumber(),
                new StoredPartRecord(remote.partNumber(), remote.etag(), remote.sizeBytes()));
          }
          session.updateUploadedPartsJson(writePartsMap(merged), Instant.now());
          sessions.saveAndFlush(session);
        }
      } catch (IOException ignored) {
        // Fall back to DB-recorded part state when remote listing is temporarily unavailable.
      }
    }
    return toResponse(session);
  }

  @Transactional
  public EvidenceUploadSessionResponse uploadPart(
      UUID actorId, UUID inspectionId, UUID sessionId, int partNumber, MultipartFile chunk) {
    Inspection inspection = evidenceService.requireAssignedInspection(actorId, inspectionId);
    evidenceService.requireEvidenceEditable(inspectionId, inspection);
    EvidenceUploadSession session = requireSession(inspectionId, sessionId);
    Instant now = Instant.now();
    if (expireIfNeeded(session, now)) {
      throw new BusinessException(
          HttpStatus.GONE, "UPLOAD_SESSION_EXPIRED", "Upload session has expired");
    }
    if (session.getStatus() != EvidenceUploadSessionStatus.OPEN) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "UPLOAD_SESSION_NOT_OPEN",
          "Upload session is " + session.getStatus());
    }
    if (partNumber < 1 || partNumber > session.getTotalParts()) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "UPLOAD_PART_INVALID",
          "Part number must be between 1 and " + session.getTotalParts());
    }
    if (chunk == null || chunk.isEmpty()) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "UPLOAD_PART_INVALID", "Upload chunk is empty");
    }
    long expectedSize = session.expectedPartSize(partNumber);
    if (chunk.getSize() != expectedSize) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "UPLOAD_PART_SIZE_MISMATCH",
          "Expected " + expectedSize + " bytes for part " + partNumber);
    }

    EvidenceObjectStore store = requireObjectStore();
    String etag;
    try (InputStream input = chunk.getInputStream()) {
      etag =
          store.uploadPart(
              session.getRawObjectKey(),
              session.getStorageUploadId(),
              partNumber,
              expectedSize,
              input);
    } catch (IOException ex) {
      throw new BusinessException(
          HttpStatus.BAD_GATEWAY, "EVIDENCE_STORAGE_UNAVAILABLE", "Failed to store upload chunk");
    }

    Map<Integer, StoredPartRecord> parts = readPartsMap(session.getUploadedPartsJson());
    parts.put(partNumber, new StoredPartRecord(partNumber, etag, expectedSize));
    session.updateUploadedPartsJson(writePartsMap(parts), Instant.now());
    EvidenceUploadSession saved = sessions.saveAndFlush(session);
    return toResponse(saved);
  }

  @Transactional
  public EvidenceUploadSessionResponse completeUpload(
      UUID actorId, UUID inspectionId, UUID sessionId) {
    Inspection inspection = evidenceService.requireAssignedInspection(actorId, inspectionId);
    evidenceService.requireEvidenceEditable(inspectionId, inspection);
    EvidenceUploadSession session = requireSession(inspectionId, sessionId);
    Instant now = Instant.now();

    if (session.getStatus() == EvidenceUploadSessionStatus.PROCESSING
        || session.getStatus() == EvidenceUploadSessionStatus.READY) {
      return toResponse(session);
    }
    if (expireIfNeeded(session, now)) {
      throw new BusinessException(
          HttpStatus.GONE, "UPLOAD_SESSION_EXPIRED", "Upload session has expired");
    }
    if (session.getStatus() != EvidenceUploadSessionStatus.OPEN
        && session.getStatus() != EvidenceUploadSessionStatus.COMPLETING) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "UPLOAD_SESSION_NOT_OPEN",
          "Upload session cannot be completed from status " + session.getStatus());
    }

    ensureNoFilenameConflict(inspectionId, session.getFileName());

    EvidenceObjectStore store = requireObjectStore();
    Map<Integer, StoredPartRecord> partsMap = readPartsMap(session.getUploadedPartsJson());
    if (partsMap.size() < session.getTotalParts()) {
      try {
        for (EvidenceObjectStore.UploadedPart remote :
            store.listUploadedParts(session.getRawObjectKey(), session.getStorageUploadId())) {
          partsMap.putIfAbsent(
              remote.partNumber(),
              new StoredPartRecord(remote.partNumber(), remote.etag(), remote.sizeBytes()));
        }
      } catch (IOException ignored) {
      }
    }

    List<EvidenceObjectStore.UploadedPart> orderedParts = new ArrayList<>();
    for (int partNo = 1; partNo <= session.getTotalParts(); partNo++) {
      StoredPartRecord part = partsMap.get(partNo);
      if (part == null) {
        throw new BusinessException(
            HttpStatus.CONFLICT,
            "UPLOAD_PARTS_INCOMPLETE",
            "Missing upload part " + partNo + " of " + session.getTotalParts());
      }
      long expectedSize = session.expectedPartSize(partNo);
      if (part.sizeBytes() > 0 && part.sizeBytes() != expectedSize) {
        throw new BusinessException(
            HttpStatus.CONFLICT,
            "UPLOAD_PART_SIZE_MISMATCH",
            "Part " + partNo + " size does not match expected " + expectedSize + " bytes");
      }
      orderedParts.add(
          new EvidenceObjectStore.UploadedPart(part.partNumber(), part.etag(), expectedSize));
    }

    session.markCompleting(now);
    sessions.saveAndFlush(session);

    try {
      store.completeMultipartUpload(
          session.getRawObjectKey(), session.getStorageUploadId(), orderedParts);
      EvidenceObjectStore.ObjectMetadata stat = store.stat(session.getRawObjectKey());
      if (stat.sizeBytes() != session.getDeclaredSizeBytes()) {
        throw new BusinessException(
            HttpStatus.CONFLICT,
            "UPLOAD_SIZE_MISMATCH",
            "Assembled object size does not match declared upload size");
      }
    } catch (BusinessException ex) {
      throw ex;
    } catch (IOException ex) {
      throw new BusinessException(
          HttpStatus.BAD_GATEWAY,
          "EVIDENCE_STORAGE_UNAVAILABLE",
          "Could not assemble uploaded video parts");
    }

    Evidence record =
        new Evidence(
            inspectionId,
            session.getFieldSessionId(),
            session.getFileName(),
            session.getContentType(),
            session.getDeclaredSizeBytes(),
            session.getRawObjectKey(),
            session.getCaptureTime(),
            session.getLatitude(),
            session.getLongitude(),
            session.getLocationDescription(),
            Instant.now());
    record.markVideoPending(session.getRawObjectKey(), session.getDeclaredSizeBytes());
    Evidence savedEvidence = evidence.saveAndFlush(record);

    session.markProcessing(savedEvidence.getId(), Instant.now());
    EvidenceUploadSession savedSession = sessions.saveAndFlush(session);

    evidenceService.syncFindingsConfirmationAfterEvidenceEdit(inspection);
    videoWorker.ifPresent(worker -> worker.scheduleAfterCommit(savedEvidence.getId()));
    return toResponse(savedSession);
  }

  @Transactional
  public EvidenceUploadSessionResponse cancelUpload(
      UUID actorId, UUID inspectionId, UUID sessionId) {
    evidenceService.requireAssignedInspection(actorId, inspectionId);
    EvidenceUploadSession session = requireSession(inspectionId, sessionId);
    if (session.getStatus() == EvidenceUploadSessionStatus.CANCELLED) {
      return toResponse(session);
    }
    if (session.getStatus() == EvidenceUploadSessionStatus.PROCESSING
        || session.getStatus() == EvidenceUploadSessionStatus.READY) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "UPLOAD_ALREADY_COMPLETED", "Completed upload cannot be cancelled");
    }
    abortStorageMultipart(session);
    session.markCancelled(Instant.now());
    return toResponse(sessions.saveAndFlush(session));
  }

  @Scheduled(fixedDelayString = "${app.evidence.upload-cleanup-interval:300000}")
  @Transactional
  public void cleanupExpiredSessions() {
    Instant now = Instant.now();
    List<EvidenceUploadSession> expired =
        sessions.findByStatusAndExpiresAtBefore(EvidenceUploadSessionStatus.OPEN, now);
    for (EvidenceUploadSession session : expired) {
      abortStorageMultipart(session);
      session.markExpired(now);
      sessions.save(session);
    }
  }

  private boolean expireIfNeeded(EvidenceUploadSession session, Instant now) {
    if (!session.isExpired(now)) {
      return false;
    }
    abortStorageMultipart(session);
    session.markExpired(now);
    sessions.saveAndFlush(session);
    return true;
  }

  private void abortStorageMultipart(EvidenceUploadSession session) {
    if (objectStore.isEmpty() || session.getStorageUploadId() == null) {
      return;
    }
    try {
      objectStore
          .get()
          .abortMultipartUpload(session.getRawObjectKey(), session.getStorageUploadId());
    } catch (Exception ex) {
      LOG.warn("Failed to abort multipart upload for session {}", session.getId(), ex);
    }
  }

  private void ensureNoFilenameConflict(UUID inspectionId, String fileName) {
    Optional<Evidence> existing =
        evidence.findFirstByInspectionIdAndFileNameOrderByCreatedAtAsc(inspectionId, fileName);
    if (existing.isPresent()) {
      if (existing.get().isDeleted()) {
        throw new BusinessException(
            HttpStatus.CONFLICT,
            "EVIDENCE_DUPLICATE_RETIRED",
            "Evidence with this filename already exists as a retired audit copy: " + fileName);
      }
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "EVIDENCE_DUPLICATE_FILENAME",
          "Evidence with this filename already exists: " + fileName);
    }
  }

  private EvidenceUploadSession requireSession(UUID inspectionId, UUID sessionId) {
    return sessions
        .findByIdAndInspectionId(sessionId, inspectionId)
        .orElseThrow(
            () ->
                new BusinessException(
                    HttpStatus.NOT_FOUND, "UPLOAD_SESSION_NOT_FOUND", "Upload session not found"));
  }

  private EvidenceObjectStore requireObjectStore() {
    return objectStore.orElseThrow(
        () ->
            new BusinessException(
                HttpStatus.BAD_GATEWAY,
                "EVIDENCE_STORAGE_UNAVAILABLE",
                "Evidence storage is not configured"));
  }

  private Map<Integer, StoredPartRecord> readPartsMap(String json) {
    Map<Integer, StoredPartRecord> map = new LinkedHashMap<>();
    if (json == null || json.isBlank()) {
      return map;
    }
    try {
      List<StoredPartRecord> list = objectMapper.readValue(json, PART_LIST_TYPE);
      if (list != null) {
        for (StoredPartRecord item : list) {
          map.put(item.partNumber(), item);
        }
      }
    } catch (Exception ex) {
      LOG.warn("Failed to parse uploaded_parts_json", ex);
    }
    return map;
  }

  private String writePartsMap(Map<Integer, StoredPartRecord> map) {
    try {
      List<StoredPartRecord> sorted =
          map.values().stream()
              .sorted(Comparator.comparingInt(StoredPartRecord::partNumber))
              .toList();
      return objectMapper.writeValueAsString(sorted);
    } catch (Exception ex) {
      throw new IllegalStateException("Failed to serialize uploaded parts", ex);
    }
  }

  private EvidenceUploadSessionResponse toResponse(EvidenceUploadSession session) {
    List<Integer> uploadedPartNumbers =
        readPartsMap(session.getUploadedPartsJson()).keySet().stream().sorted().toList();
    EvidenceProcessingStatus processingStatus = null;
    if (session.getEvidenceId() != null) {
      processingStatus =
          evidence
              .findById(session.getEvidenceId())
              .map(Evidence::getProcessingStatus)
              .orElse(null);
    }
    return new EvidenceUploadSessionResponse(
        session.getId(),
        session.getInspectionId(),
        session.getFieldSessionId(),
        session.getFileName(),
        session.getContentType(),
        session.getDeclaredSizeBytes(),
        session.getChunkSizeBytes(),
        session.getTotalParts(),
        uploadedPartNumbers,
        session.getStatus(),
        session.getEvidenceId(),
        processingStatus,
        session.getErrorMessage(),
        session.getExpiresAt(),
        session.getCreatedAt(),
        session.getUpdatedAt(),
        session.getCompletedAt());
  }

  private record StoredPartRecord(int partNumber, String etag, long sizeBytes) {}
}
