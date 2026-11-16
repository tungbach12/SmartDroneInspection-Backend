package com.smartdroneinspection.inspections.service;

import com.smartdroneinspection.inspections.api.dto.EvidenceMediaTypeFilter;
import com.smartdroneinspection.inspections.api.dto.EvidenceStatusFilter;
import com.smartdroneinspection.inspections.api.dto.response.EvidencePageResponse;
import com.smartdroneinspection.inspections.api.dto.response.EvidenceResponse;
import com.smartdroneinspection.inspections.domain.Evidence;
import com.smartdroneinspection.inspections.domain.FieldSession;
import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.domain.InspectionReport;
import com.smartdroneinspection.inspections.domain.enums.EvidenceProcessingStatus;
import com.smartdroneinspection.inspections.domain.enums.InspectionStatus;
import com.smartdroneinspection.inspections.domain.enums.ReportStatus;
import com.smartdroneinspection.inspections.repository.EvidenceRepository;
import com.smartdroneinspection.inspections.repository.FieldSessionRepository;
import com.smartdroneinspection.inspections.repository.FindingRepository;
import com.smartdroneinspection.inspections.repository.InspectionReportRepository;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.shared.auth.Roles;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.shared.storage.EvidenceObjectStore;
import com.smartdroneinspection.users.UserAccess;
import jakarta.persistence.criteria.Predicate;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
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
@EnableConfigurationProperties(EvidenceUploadProperties.class)
public class EvidenceService {

  static final long MAX_SIZE_BYTES = 10L * 1024 * 1024;
  static final long MAX_VIDEO_SIZE_BYTES = 20L * 1024 * 1024;
  private static final int MAX_EVIDENCE_PAGE_SIZE = 100;
  private static final Sort EVIDENCE_ORDER =
      Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id"));
  private static final char LIKE_ESCAPE = '\\';
  private static final Set<String> ALLOWED_TYPES =
      Set.of("image/png", "image/jpeg", "image/webp", "image/tiff", "video/mp4", "application/pdf");

  /** Image types whose bytes must decode before the evidence is stored. */
  private static final Set<String> DECODABLE_IMAGE_TYPES =
      Set.of("image/png", "image/jpeg", "image/webp", "image/tiff");

  private final InspectionRepository inspections;
  private final EvidenceRepository evidence;
  private final InspectionReportRepository reports;
  private final FindingRepository findings;
  private final FieldSessionRepository sessions;
  private final Optional<EvidenceObjectStore> objectStore;
  private final UserAccess userAccess;
  private final EvidenceUploadProperties uploadProperties;
  private final Optional<VideoProcessingWorker> videoWorker;

  public EvidenceService(
      InspectionRepository inspections,
      EvidenceRepository evidence,
      InspectionReportRepository reports,
      FindingRepository findings,
      FieldSessionRepository sessions,
      Optional<EvidenceObjectStore> objectStore,
      UserAccess userAccess) {
    this(
        inspections,
        evidence,
        reports,
        findings,
        sessions,
        objectStore,
        userAccess,
        new EvidenceUploadProperties(),
        Optional.empty());
  }

  @Autowired
  public EvidenceService(
      InspectionRepository inspections,
      EvidenceRepository evidence,
      InspectionReportRepository reports,
      FindingRepository findings,
      FieldSessionRepository sessions,
      Optional<EvidenceObjectStore> objectStore,
      UserAccess userAccess,
      EvidenceUploadProperties uploadProperties,
      Optional<VideoProcessingWorker> videoWorker) {
    this.inspections = inspections;
    this.evidence = evidence;
    this.reports = reports;
    this.findings = findings;
    this.sessions = sessions;
    this.objectStore = objectStore;
    this.userAccess = userAccess;
    this.uploadProperties =
        uploadProperties != null ? uploadProperties : new EvidenceUploadProperties();
    this.videoWorker = videoWorker != null ? videoWorker : Optional.empty();
  }

  @Transactional
  public EvidenceResponse upload(
      UUID actorId,
      UUID inspectionId,
      UUID fieldSessionId,
      MultipartFile file,
      Instant captureTime,
      BigDecimal latitude,
      BigDecimal longitude) {
    return upload(
        actorId, inspectionId, fieldSessionId, file, captureTime, latitude, longitude, null, null);
  }

  public EvidenceResponse upload(
      UUID actorId,
      UUID inspectionId,
      UUID fieldSessionId,
      MultipartFile file,
      Instant captureTime,
      BigDecimal latitude,
      BigDecimal longitude,
      UUID sourceVideoEvidenceId,
      Double sourceTimestampSeconds) {
    if (file == null || file.isEmpty()) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "EVIDENCE_INVALID", "The uploaded file is empty");
    }
    String rawContentType = file.getContentType();
    String contentType = rawContentType == null ? "" : rawContentType.toLowerCase(Locale.ROOT);
    long maxAllowedBytes =
        "video/mp4".equals(contentType)
            ? uploadProperties.getMaxSingleUploadVideoSizeBytes()
            : uploadProperties.getMaxImageSizeBytes();
    if (file.getSize() > maxAllowedBytes) {
      throw new BusinessException(
          HttpStatus.PAYLOAD_TOO_LARGE,
          "EVIDENCE_TOO_LARGE",
          "Maximum single-request evidence size is " + (maxAllowedBytes / (1024 * 1024)) + " MB");
    }
    byte[] content;
    try {
      content = file.getBytes();
    } catch (IOException ex) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "EVIDENCE_INVALID", "The uploaded file could not be read");
    }
    return storeCapturedMedia(
        actorId,
        inspectionId,
        fieldSessionId,
        file.getOriginalFilename(),
        file.getContentType(),
        content,
        captureTime,
        latitude,
        longitude,
        null,
        sourceVideoEvidenceId,
        sourceTimestampSeconds);
  }

  @Transactional
  public EvidenceResponse storeCapturedMedia(
      UUID actorId,
      UUID inspectionId,
      UUID fieldSessionId,
      String rawFileName,
      String rawContentType,
      byte[] content,
      Instant captureTime,
      BigDecimal latitude,
      BigDecimal longitude,
      String locationDescription) {
    return storeCapturedMedia(
        actorId,
        inspectionId,
        fieldSessionId,
        rawFileName,
        rawContentType,
        content,
        captureTime,
        latitude,
        longitude,
        locationDescription,
        null,
        null);
  }

  @Transactional
  public EvidenceResponse storeCapturedMedia(
      UUID actorId,
      UUID inspectionId,
      UUID fieldSessionId,
      String rawFileName,
      String rawContentType,
      byte[] content,
      Instant captureTime,
      BigDecimal latitude,
      BigDecimal longitude,
      String locationDescription,
      UUID sourceVideoEvidenceId,
      Double sourceTimestampSeconds) {

    Inspection inspection = requireAssignedInspection(actorId, inspectionId);
    requireEvidenceEditable(inspectionId, inspection);
    if (sourceTimestampSeconds != null
        && (Double.isNaN(sourceTimestampSeconds)
            || Double.isInfinite(sourceTimestampSeconds)
            || sourceTimestampSeconds < 0.0)) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "EVIDENCE_INVALID",
          "Source video timestamp must be zero or positive");
    }
    if (sourceTimestampSeconds != null && sourceVideoEvidenceId == null) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "EVIDENCE_INVALID",
          "sourceVideoEvidenceId is required when sourceTimestampSeconds is supplied");
    }
    Evidence sourceVideo = null;
    if (sourceVideoEvidenceId != null) {
      sourceVideo =
          evidence
              .findByIdAndInspectionId(sourceVideoEvidenceId, inspectionId)
              .orElseThrow(
                  () ->
                      new BusinessException(
                          HttpStatus.BAD_REQUEST,
                          "EVIDENCE_INVALID",
                          "Source video evidence not found in this inspection"));
      if (sourceVideo.isDeleted() || !sourceVideo.isVideo()) {
        throw new BusinessException(
            HttpStatus.BAD_REQUEST,
            "EVIDENCE_INVALID",
            "Source evidence must be an active video item");
      }
      if (fieldSessionId == null && sourceVideo.getFieldSessionId() != null) {
        fieldSessionId = sourceVideo.getFieldSessionId();
      }
      if (latitude == null && longitude == null && sourceVideo.getLatitude() != null) {
        latitude = sourceVideo.getLatitude();
        longitude = sourceVideo.getLongitude();
      }
    }
    if (fieldSessionId != null) {
      requireSessionInScope(inspection, fieldSessionId);
    }
    String contentType = rawContentType == null ? "" : rawContentType.toLowerCase(Locale.ROOT);
    if (!ALLOWED_TYPES.contains(contentType)) {
      throw new BusinessException(
          HttpStatus.UNSUPPORTED_MEDIA_TYPE,
          "EVIDENCE_INVALID",
          "Unsupported evidence type. Allowed: png, jpeg, webp, tiff, mp4, pdf");
    }
    if (content == null || content.length == 0) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "EVIDENCE_INVALID", "The uploaded file is empty");
    }
    long maxAllowedBytes =
        "video/mp4".equals(contentType)
            ? uploadProperties.getMaxSingleUploadVideoSizeBytes()
            : uploadProperties.getMaxImageSizeBytes();
    if (content.length > maxAllowedBytes) {
      throw new BusinessException(
          HttpStatus.PAYLOAD_TOO_LARGE,
          "EVIDENCE_TOO_LARGE",
          "Maximum evidence size is " + (maxAllowedBytes / (1024 * 1024)) + " MB");
    }

    // Extract EXIF GPS & capture time BEFORE any decoding or re-encoding strips metadata.
    if ((latitude == null && longitude == null) || captureTime == null) {
      ExifMetadataExtractor.ExtractedMetadata extracted = ExifMetadataExtractor.extract(content);
      if (extracted != null) {
        if (latitude == null && longitude == null) {
          latitude = extracted.latitude();
          longitude = extracted.longitude();
        }
        if (captureTime == null) {
          captureTime = extracted.captureTime();
        }
      }
    }

    validateGpsPair(latitude, longitude);

    // Report 3 MF3-02 requires corrupt input to be identified with a corrective action. Declared
    // type alone is not proof of content, so image bytes must decode before they are stored.
    ImageNormalizer.NormalizedImage normalizedImage = null;
    if (DECODABLE_IMAGE_TYPES.contains(contentType)) {
      try {
        normalizedImage = ImageNormalizer.normalize(content, contentType);
      } catch (IOException ex) {
        throw new BusinessException(
            HttpStatus.BAD_REQUEST,
            "EVIDENCE_INVALID",
            "The uploaded file is not a readable " + contentType + " image");
      }
      if (normalizedImage == null) {
        throw new BusinessException(
            HttpStatus.BAD_REQUEST,
            "EVIDENCE_INVALID",
            "The uploaded file is not a readable " + contentType + " image");
      }
    }

    String fileName = sanitizeFileName(rawFileName);
    Optional<Evidence> sameName =
        evidence.findFirstByInspectionIdAndFileNameOrderByCreatedAtAsc(inspectionId, fileName);
    if (sameName.isPresent()) {
      Evidence existing = sameName.get();
      if (existing.isDeleted()) {
        throw new BusinessException(
            HttpStatus.CONFLICT,
            "EVIDENCE_DUPLICATE_RETIRED",
            "Evidence with this filename already exists as a retired audit copy: " + fileName);
      }
      return toResponse(existing);
    }

    UUID objectUuid = UUID.randomUUID();
    String objectKey = "inspections/" + inspectionId + "/evidence/" + objectUuid;
    EvidenceObjectStore store = requireObjectStore();

    byte[] storedBytes = normalizedImage != null ? normalizedImage.primaryBytes() : content;
    String storedContentType =
        normalizedImage != null ? normalizedImage.primaryContentType() : contentType;
    String thumbObjectKey = null;
    Long thumbSizeBytes = null;

    boolean primaryUploaded = false;
    boolean thumbUploaded = false;
    try {
      store.put(
          objectKey, storedContentType, storedBytes.length, new ByteArrayInputStream(storedBytes));
      primaryUploaded = true;

      if (normalizedImage != null) {
        String thumbExt =
            "image/png".equals(normalizedImage.thumbnailContentType()) ? ".png" : ".jpg";
        thumbObjectKey = objectKey + "/thumb" + thumbExt;
        byte[] thumbBytes = normalizedImage.thumbnailBytes();
        store.put(
            thumbObjectKey,
            normalizedImage.thumbnailContentType(),
            thumbBytes.length,
            new ByteArrayInputStream(thumbBytes));
        thumbUploaded = true;
        thumbSizeBytes = (long) thumbBytes.length;
      }
    } catch (IOException ex) {
      if (thumbUploaded) {
        deleteQuietly(store, thumbObjectKey);
      }
      if (primaryUploaded) {
        deleteQuietly(store, objectKey);
      }
      throw new BusinessException(
          HttpStatus.BAD_GATEWAY,
          "EVIDENCE_STORAGE_UNAVAILABLE",
          "Evidence storage is unavailable");
    }

    String normalizedLocation =
        locationDescription == null || locationDescription.isBlank()
            ? null
            : locationDescription.trim();
    Evidence record =
        new Evidence(
            inspectionId,
            fieldSessionId,
            fileName,
            storedContentType,
            storedBytes.length,
            objectKey,
            captureTime,
            latitude,
            longitude,
            normalizedLocation,
            Instant.now());
    if (sourceVideoEvidenceId != null) {
      record.linkVideoSource(sourceVideoEvidenceId, sourceTimestampSeconds);
    }
    if (normalizedImage != null) {
      record.applyImageOptimization(
          objectKey,
          storedContentType,
          storedBytes.length,
          content.length,
          thumbObjectKey,
          thumbSizeBytes,
          normalizedImage.width(),
          normalizedImage.height(),
          Instant.now());
    } else if ("video/mp4".equals(storedContentType) && videoWorker.isPresent()) {
      record.markVideoPending(objectKey, content.length);
    }

    Evidence savedRecord;
    try {
      savedRecord = evidence.saveAndFlush(record);
    } catch (RuntimeException ex) {
      if (thumbUploaded) {
        deleteQuietly(store, thumbObjectKey);
      }
      if (primaryUploaded) {
        deleteQuietly(store, objectKey);
      }
      throw ex;
    }

    if (savedRecord.getProcessingStatus() == EvidenceProcessingStatus.PENDING) {
      videoWorker.ifPresent(worker -> worker.scheduleAfterCommit(savedRecord.getId()));
    }

    syncFindingsConfirmationAfterEvidenceEdit(inspection);
    return toResponse(savedRecord);
  }

  @Transactional
  public EvidenceResponse retryProcessing(UUID actorId, UUID inspectionId, UUID evidenceId) {
    Inspection inspection = requireAssignedInspection(actorId, inspectionId);
    requireEvidenceEditable(inspectionId, inspection);
    Evidence record =
        evidence
            .findByIdAndInspectionId(evidenceId, inspectionId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND, "EVIDENCE_NOT_FOUND", "Evidence not found"));
    if (record.isDeleted()) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "EVIDENCE_RETIRED", "Retired evidence cannot be reprocessed");
    }
    if (record.getProcessingStatus() != EvidenceProcessingStatus.FAILED) {
      return toResponse(record);
    }
    try {
      record.requestRetry();
    } catch (IllegalStateException ex) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "EVIDENCE_RETRY_UNAVAILABLE", ex.getMessage());
    }
    Evidence saved = evidence.saveAndFlush(record);
    videoWorker.ifPresent(worker -> worker.scheduleAfterCommit(saved.getId()));
    return toResponse(saved);
  }

  @Transactional
  public EvidenceResponse updateLocation(
      UUID actorId, UUID inspectionId, UUID evidenceId, String locationDescription) {
    Inspection inspection = requireAssignedInspection(actorId, inspectionId);
    requireEvidenceEditable(inspectionId, inspection);
    Evidence record =
        evidence
            .findByIdAndInspectionId(evidenceId, inspectionId)
            .orElseThrow(this::inspectionNotFound);
    String normalized =
        locationDescription == null || locationDescription.isBlank()
            ? null
            : locationDescription.trim();
    if (!java.util.Objects.equals(record.getLocationDescription(), normalized)) {
      record.setLocationDescription(normalized);
      evidence.saveAndFlush(record);
      syncFindingsConfirmationAfterEvidenceEdit(inspection);
    }
    return toResponse(record);
  }

  /**
   * MF3-02: retire an original capture without removing its bytes or metadata. A live finding must
   * first be removed or re-linked, so the next report cannot silently cite a retired capture.
   */
  @Transactional
  public EvidenceResponse retire(UUID actorId, UUID inspectionId, UUID evidenceId) {
    Inspection inspection = requireAssignedInspection(actorId, inspectionId);
    requireEvidenceEditable(inspectionId, inspection);
    Evidence record =
        evidence
            .findByIdAndInspectionId(evidenceId, inspectionId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND, "EVIDENCE_NOT_FOUND", "Evidence not found"));
    boolean hasActiveFinding =
        findings.findByInspectionIdOrderByCreatedAtAsc(inspectionId).stream()
            .anyMatch(finding -> evidenceId.equals(finding.getEvidenceId()));
    if (hasActiveFinding) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "EVIDENCE_HAS_ACTIVE_FINDINGS",
          "Remove or re-link the findings that cite this capture before retiring it");
    }
    try {
      record.retire();
    } catch (IllegalStateException ex) {
      throw new BusinessException(HttpStatus.CONFLICT, "EVIDENCE_RETIRE_INVALID", ex.getMessage());
    }
    Evidence savedRecord = evidence.saveAndFlush(record);
    syncFindingsConfirmationAfterEvidenceEdit(inspection);
    return toResponse(savedRecord);
  }

  /**
   * MF3-02: bulk retirement of captures without deleting their stored bytes.
   *
   * <p>If {@code evidenceIds} is empty or null, all unretired captures for the inspection are
   * retired.
   */
  @Transactional
  public List<EvidenceResponse> bulkRetire(
      UUID actorId, UUID inspectionId, List<UUID> evidenceIds) {
    Inspection inspection = requireAssignedInspection(actorId, inspectionId);
    requireEvidenceEditable(inspectionId, inspection);
    List<Evidence> activeCaptures =
        evidence.findByInspectionIdAndIsDeletedFalseOrderByCreatedAtAsc(inspectionId);
    List<Evidence> targets;
    if (evidenceIds == null || evidenceIds.isEmpty()) {
      targets = activeCaptures;
    } else {
      Set<UUID> idSet = new java.util.HashSet<>(evidenceIds);
      targets = activeCaptures.stream().filter(e -> idSet.contains(e.getId())).toList();
    }
    if (targets.isEmpty()) {
      return List.of();
    }
    Set<UUID> targetIds =
        targets.stream().map(Evidence::getId).collect(java.util.stream.Collectors.toSet());
    boolean hasActiveFinding =
        findings.findByInspectionIdOrderByCreatedAtAsc(inspectionId).stream()
            .anyMatch(finding -> targetIds.contains(finding.getEvidenceId()));
    if (hasActiveFinding) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "EVIDENCE_HAS_ACTIVE_FINDINGS",
          "Remove or re-link the findings that cite these captures before retiring them");
    }
    for (Evidence target : targets) {
      target.retire();
    }
    List<Evidence> saved = evidence.saveAllAndFlush(targets);
    syncFindingsConfirmationAfterEvidenceEdit(inspection);
    return saved.stream().map(this::toResponse).toList();
  }

  void syncFindingsConfirmationAfterEvidenceEdit(Inspection inspection) {
    if (inspection.getStatus() == InspectionStatus.FIELD_COMPLETED
        && inspection.hasFindingsConfirmation()) {
      inspection.reopenFindings();
      inspections.saveAndFlush(inspection);
    }
  }

  void requireSessionInScope(Inspection inspection, UUID fieldSessionId) {
    FieldSession session =
        sessions
            .findByIdAndInspectionId(fieldSessionId, inspection.getId())
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.CONFLICT,
                        "FIELD_SESSION_NOT_FOUND",
                        "The field session was not found in this inspection."));
    if (!"IN_PROGRESS".equals(session.getStatus())
        && !"FIELD_COMPLETED".equals(session.getStatus())) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "FIELD_SESSION_NOT_CURRENT",
          "Evidence must belong to your active or completed field session.");
    }
  }

  void requireEvidenceEditable(UUID inspectionId, Inspection inspection) {
    if (inspection.getStatus() == InspectionStatus.SCHEDULED
        || inspection.getStatus() == InspectionStatus.CANCELLED
        || inspection.getStatus() == InspectionStatus.COMPLETED) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "INSPECTION_STATE_CONFLICT",
          "Evidence cannot be modified while the inspection is " + inspection.getStatus());
    }
    InspectionReport report =
        reports.findFirstByInspectionIdOrderByVersionNoDesc(inspectionId).orElse(null);
    if (report == null) return;
    if (report.getStatus() == ReportStatus.SUBMITTED
        || report.getStatus() == ReportStatus.PUBLISHED
        || report.getStatus() == ReportStatus.SUPERSEDED) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "EVIDENCE_LOCKED",
          "Withdraw the submitted report or ask the reviewer to return it before changing evidence");
    }
  }

  @Transactional(readOnly = true)
  public List<EvidenceResponse> listForFieldSession(UUID inspectionId, UUID fieldSessionId) {
    return evidence
        .findByInspectionIdAndFieldSessionIdAndIsDeletedFalseOrderByCreatedAtAsc(
            inspectionId, fieldSessionId)
        .stream()
        .map(this::toResponse)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<EvidenceResponse> list(UUID actorId, UUID inspectionId) {
    requireInspectionInScope(actorId, inspectionId);
    return evidence.findByInspectionIdOrderByCreatedAtAsc(inspectionId).stream()
        .map(this::toResponse)
        .toList();
  }

  @Transactional(readOnly = true)
  public EvidencePageResponse query(
      UUID actorId,
      UUID inspectionId,
      EvidenceStatusFilter status,
      EvidenceMediaTypeFilter mediaType,
      String search,
      int page,
      int pageSize) {
    requireInspectionInScope(actorId, inspectionId);
    int safePage = Math.max(page, 1);
    int safePageSize = clampEvidencePageSize(pageSize);
    Page<Evidence> result =
        evidence.findAll(
            evidenceFilter(inspectionId, status, mediaType, search),
            PageRequest.of(safePage - 1, safePageSize, EVIDENCE_ORDER));
    EvidenceRepository.EvidenceSummary summary = evidence.summarizeInspectionEvidence(inspectionId);
    Instant latestCreatedAt = summary != null ? summary.getLatestCreatedAt() : null;
    Instant latestRetiredAt = summary != null ? summary.getLatestRetiredAt() : null;
    return new EvidencePageResponse(
        result.getContent().stream().map(this::toResponse).toList(),
        safePage,
        safePageSize,
        result.getTotalElements(),
        result.getTotalPages(),
        evidence.countByInspectionIdAndIsDeletedFalse(inspectionId),
        evidence.countByInspectionIdAndIsDeletedTrue(inspectionId),
        latestCreatedAt,
        latestRetiredAt);
  }

  private static Specification<Evidence> evidenceFilter(
      UUID inspectionId,
      EvidenceStatusFilter status,
      EvidenceMediaTypeFilter mediaType,
      String search) {
    return (root, query, builder) -> {
      var predicates = new java.util.ArrayList<Predicate>();
      predicates.add(builder.equal(root.get("inspectionId"), inspectionId));
      switch (status) {
        case ACTIVE -> predicates.add(builder.isFalse(root.get("isDeleted")));
        case RETIRED -> predicates.add(builder.isTrue(root.get("isDeleted")));
        case ALL -> {}
      }
      var contentType = builder.lower(root.<String>get("contentType"));
      switch (mediaType) {
        case IMAGE -> predicates.add(builder.like(contentType, "image/%"));
        case VIDEO -> predicates.add(builder.like(contentType, "video/%"));
        case DOCUMENT -> {
          predicates.add(builder.notLike(contentType, "image/%"));
          predicates.add(builder.notLike(contentType, "video/%"));
        }
        case ALL -> {}
      }
      if (search != null && !search.isBlank()) {
        String term = "%" + escapeLike(search.trim().toLowerCase(Locale.ROOT)) + "%";
        predicates.add(
            builder.or(
                builder.like(builder.lower(root.<String>get("fileName")), term, LIKE_ESCAPE),
                builder.like(contentType, term, LIKE_ESCAPE)));
      }
      return builder.and(predicates.toArray(Predicate[]::new));
    };
  }

  private static String escapeLike(String term) {
    return term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
  }

  private static int clampEvidencePageSize(int pageSize) {
    return Math.min(Math.max(pageSize, 1), MAX_EVIDENCE_PAGE_SIZE);
  }

  @Transactional(readOnly = true)
  public EvidenceContent open(UUID actorId, UUID inspectionId, UUID evidenceId) {
    MediaDescriptor descriptor =
        resolveMediaDescriptor(actorId, inspectionId, evidenceId, "primary", false);
    try {
      return new EvidenceContent(
          descriptor.contentType(),
          descriptor.fileName(),
          requireObjectStore().open(descriptor.objectKey()));
    } catch (IOException ex) {
      throw new BusinessException(
          HttpStatus.BAD_GATEWAY,
          "EVIDENCE_STORAGE_UNAVAILABLE",
          "Evidence storage is unavailable");
    }
  }

  @Transactional(readOnly = true)
  public MediaDescriptor resolveMediaDescriptor(
      UUID actorId, UUID inspectionId, UUID evidenceId, String rawVariant, boolean rejectRetired) {
    requireInspectionInScope(actorId, inspectionId);
    Evidence record =
        evidence
            .findByIdAndInspectionId(evidenceId, inspectionId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND, "EVIDENCE_NOT_FOUND", "Evidence not found"));
    if (rejectRetired && record.isDeleted()) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "EVIDENCE_RETIRED", "Retired evidence cannot be streamed");
    }
    if (!record.isReady()) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "EVIDENCE_NOT_READY",
          "Evidence media is not ready for playback yet");
    }
    String variant = MediaTicketService.normalizeVariant(rawVariant);
    String key;
    String contentType;
    long knownSize;
    if ("thumb".equals(variant)) {
      if (record.getThumbnailObjectKey() != null && !record.getThumbnailObjectKey().isBlank()) {
        key = record.getThumbnailObjectKey();
        contentType = key.endsWith(".png") ? "image/png" : "image/jpeg";
        knownSize = record.getThumbnailSizeBytes() != null ? record.getThumbnailSizeBytes() : -1L;
      } else if (record.isImage()) {
        key = record.getObjectKey();
        contentType = record.getContentType();
        knownSize = record.getSizeBytes();
      } else {
        throw new BusinessException(
            HttpStatus.NOT_FOUND,
            "EVIDENCE_THUMBNAIL_UNAVAILABLE",
            "No thumbnail is available for this evidence");
      }
    } else {
      key = record.getObjectKey();
      contentType = record.getContentType();
      knownSize = record.getSizeBytes();
    }

    long totalBytes = knownSize;
    EvidenceObjectStore store = requireObjectStore();
    try {
      EvidenceObjectStore.ObjectMetadata meta = store.stat(key);
      if (meta.sizeBytes() > 0) {
        totalBytes = meta.sizeBytes();
      }
    } catch (UnsupportedOperationException ignored) {
      // Custom mock stores in unit tests may not implement stat().
    } catch (IOException ex) {
      throw new BusinessException(
          HttpStatus.BAD_GATEWAY,
          "EVIDENCE_STORAGE_UNAVAILABLE",
          "Evidence storage is unavailable");
    }

    if (totalBytes <= 0) {
      throw new BusinessException(
          HttpStatus.BAD_GATEWAY,
          "EVIDENCE_STORAGE_UNAVAILABLE",
          "Stored evidence size could not be determined");
    }
    return new MediaDescriptor(key, contentType, record.getFileName(), totalBytes);
  }

  public InputStream openStream(String objectKey) {
    try {
      return requireObjectStore().open(objectKey);
    } catch (IOException ex) {
      throw new BusinessException(
          HttpStatus.BAD_GATEWAY,
          "EVIDENCE_STORAGE_UNAVAILABLE",
          "Evidence storage is unavailable");
    }
  }

  public InputStream openRangeStream(String objectKey, long offset, long length) {
    try {
      return requireObjectStore().openRange(objectKey, offset, length);
    } catch (UnsupportedOperationException ex) {
      // Fallback for unit test mocks that only implement open().
      try {
        InputStream full = requireObjectStore().open(objectKey);
        full.skipNBytes(offset);
        return new BoundedInputStream(full, length);
      } catch (IOException ioEx) {
        throw new BusinessException(
            HttpStatus.BAD_GATEWAY,
            "EVIDENCE_STORAGE_UNAVAILABLE",
            "Evidence storage is unavailable");
      }
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
    if (record.isRetired()) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "EVIDENCE_RETIRED", "Retired evidence cannot be analysed");
    }
    if (!record.isReady()) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "EVIDENCE_NOT_READY", "Media is still processing");
    }
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
    if (!actor.hasRole(Roles.INSPECTOR)) {
      throw inspectionNotFound();
    }
    return inspections
        .findAssignedByInspector(inspectionId, actorId)
        .orElseThrow(this::inspectionNotFound);
  }

  Inspection requireInspectionInScope(UUID actorId, UUID inspectionId) {
    UserAccess.ActiveUser actor = requireActiveUser(actorId);
    if (actor.hasRole(Roles.ADMIN) || actor.hasRole(Roles.MANAGER)) {
      return inspections.findById(inspectionId).orElseThrow(this::inspectionNotFound);
    }
    if (actor.hasRole(Roles.INSPECTOR)) {
      return inspections
          .findAssignedByInspector(inspectionId, actorId)
          .orElseThrow(this::inspectionNotFound);
    }
    if (actor.ownerCustomerId() == null) {
      throw inspectionNotFound();
    }
    Inspection inspection =
        inspections
            .findByIdAndOwnerCustomerId(inspectionId, actor.ownerCustomerId())
            .orElseThrow(this::inspectionNotFound);
    if (!inspection.getInspectorId().equals(actorId) && !actor.hasRole(Roles.CUSTOMER)) {
      throw inspectionNotFound();
    }
    return inspection;
  }

  static void validateGpsPair(BigDecimal latitude, BigDecimal longitude) {
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
  }

  static String sanitizeFileName(String name) {
    if (name == null || name.isBlank()) {
      return "evidence";
    }
    String cleaned = name.replaceAll("[\\\\/\\r\\n]", "_");
    return cleaned.length() > 500 ? cleaned.substring(0, 500) : cleaned;
  }

  private UserAccess.ActiveUser requireActiveUser(UUID actorId) {
    return userAccess
        .findActiveUser(actorId)
        .orElseThrow(
            () -> new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "User is not active"));
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

  private static void deleteQuietly(EvidenceObjectStore store, String objectKey) {
    if (objectKey == null || objectKey.isBlank()) {
      return;
    }
    try {
      store.delete(objectKey);
    } catch (IOException ignored) {
    }
  }

  /**
   * Whether image bytes actually decode. {@code ImageIO.read} returns null when no registered
   * reader recognises the content, and throws when the header is valid but the data is truncated,
   * so both outcomes mean the evidence is not usable.
   */
  @SuppressWarnings("unused")
  private static boolean isDecodableImage(byte[] content) {
    try {
      return ImageIO.read(new ByteArrayInputStream(content)) != null;
    } catch (IOException | RuntimeException ex) {
      return false;
    }
  }

  EvidenceResponse toResponse(Evidence record) {
    return new EvidenceResponse(
        record.getId(),
        record.getInspectionId(),
        record.getFieldSessionId(),
        record.getFileName(),
        record.getContentType(),
        record.getSizeBytes(),
        record.getOriginalSizeBytes(),
        record.getObjectKey(),
        record.getCaptureTime(),
        record.getLatitude(),
        record.getLongitude(),
        record.getLocationDescription(),
        record.isDeleted(),
        record.getCreatedAt(),
        record.getProcessingStatus(),
        record.getProcessingError(),
        record.getThumbnailObjectKey() != null && !record.getThumbnailObjectKey().isBlank(),
        record.getMediaWidth(),
        record.getMediaHeight(),
        record.getDurationSeconds(),
        record.getProcessedAt(),
        record.getSourceVideoEvidenceId(),
        record.getSourceTimestampSeconds());
  }

  void copyStoredObjectToFile(Evidence record, java.nio.file.Path destination) throws IOException {
    EvidenceObjectStore store = requireObjectStore();
    String key =
        record.getObjectKey() != null && !record.getObjectKey().isBlank()
            ? record.getObjectKey()
            : record.getRawObjectKey();
    try (InputStream input = store.open(key)) {
      java.nio.file.Files.copy(
          input, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }
  }

  public record EvidenceContent(String contentType, String fileName, InputStream stream) {}

  public record MediaDescriptor(
      String objectKey, String contentType, String fileName, long totalBytes) {}

  private static final class BoundedInputStream extends InputStream {
    private final InputStream delegate;
    private long remaining;

    private BoundedInputStream(InputStream delegate, long remaining) {
      this.delegate = delegate;
      this.remaining = remaining;
    }

    @Override
    public int read() throws IOException {
      if (remaining <= 0) {
        return -1;
      }
      int b = delegate.read();
      if (b >= 0) {
        remaining--;
      }
      return b;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
      if (remaining <= 0) {
        return -1;
      }
      int toRead = (int) Math.min(len, remaining);
      int read = delegate.read(b, off, toRead);
      if (read > 0) {
        remaining -= read;
      }
      return read;
    }

    @Override
    public void close() throws IOException {
      delegate.close();
    }
  }
}
