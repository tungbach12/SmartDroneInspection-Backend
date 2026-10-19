package com.smartdroneinspection.inspections.service;

import com.smartdroneinspection.inspections.api.dto.EvidenceMediaTypeFilter;
import com.smartdroneinspection.inspections.api.dto.EvidenceStatusFilter;
import com.smartdroneinspection.inspections.api.dto.response.EvidencePageResponse;
import com.smartdroneinspection.inspections.api.dto.response.EvidenceResponse;
import com.smartdroneinspection.inspections.domain.Evidence;
import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.domain.InspectionReport;
import com.smartdroneinspection.inspections.domain.enums.EvidenceKind;
import com.smartdroneinspection.inspections.domain.enums.EvidenceSource;
import com.smartdroneinspection.inspections.domain.enums.InspectionStatus;
import com.smartdroneinspection.inspections.domain.enums.ReportStatus;
import com.smartdroneinspection.inspections.repository.EvidenceRepository;
import com.smartdroneinspection.inspections.repository.InspectionReportRepository;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.inspections.repository.VerifiedFindingRepository;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.shared.storage.EvidenceObjectStore;
import com.smartdroneinspection.users.UserAccess;
import jakarta.persistence.criteria.Predicate;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.imageio.ImageIO;
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
public class EvidenceService {

  static final long MAX_SIZE_BYTES = 10L * 1024 * 1024;
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
  private final VerifiedFindingRepository findings;
  private final Optional<EvidenceObjectStore> objectStore;
  private final UserAccess userAccess;

  public EvidenceService(
      InspectionRepository inspections,
      EvidenceRepository evidence,
      InspectionReportRepository reports,
      VerifiedFindingRepository findings,
      Optional<EvidenceObjectStore> objectStore,
      UserAccess userAccess) {
    this.inspections = inspections;
    this.evidence = evidence;
    this.reports = reports;
    this.findings = findings;
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
    requireEvidenceEditable(inspectionId, inspection);
    if (inspection.getStatus() == InspectionStatus.REPORT_DRAFT) {
      inspection.reopenFindings();
      inspections.saveAndFlush(inspection);
    }
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
          HttpStatus.PAYLOAD_TOO_LARGE,
          "EVIDENCE_TOO_LARGE",
          "Maximum evidence size is " + (MAX_SIZE_BYTES / (1024 * 1024)) + " MB");
    }
    // Report 3 MF3-02 requires corrupt input to be identified with a corrective action. Declared
    // type alone is not proof of content, so image bytes must decode before they are stored.
    if (DECODABLE_IMAGE_TYPES.contains(contentType) && !isDecodableImage(content)) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "EVIDENCE_INVALID",
          "The uploaded file is not a readable " + contentType + " image");
    }
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
      Evidence duplicate = existing.get();
      if (duplicate.isRetired()) {
        throw new BusinessException(
            HttpStatus.CONFLICT,
            "EVIDENCE_DUPLICATE_RETIRED",
            "Identical evidence already exists as a retired audit copy: "
                + duplicate.getFileName());
      }
      return toResponse(duplicate, true);
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
      findings.findByInspectionIdOrderByCreatedAtAsc(inspectionId).stream()
          .filter(finding -> evidenceId.equals(finding.getEvidenceId()))
          .forEach(finding -> finding.setLocationDescription(normalized));
      findings.flush();
      if (inspection.getStatus() == InspectionStatus.REPORT_DRAFT) {
        inspection.reopenFindings();
        inspections.saveAndFlush(inspection);
      }
    }
    return toResponse(record);
  }

  /**
   * MF3-02: retire an original capture without removing its bytes or metadata. A live finding must
   * first be removed or re-linked, so the next report cannot silently cite a retired capture.
   */
  @Transactional
  public EvidenceResponse retire(UUID actorId, UUID inspectionId, UUID evidenceId, String reason) {
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
      record.retire(actorId, reason);
    } catch (IllegalArgumentException | IllegalStateException ex) {
      throw new BusinessException(HttpStatus.CONFLICT, "EVIDENCE_RETIRE_INVALID", ex.getMessage());
    }
    if (inspection.getStatus() == InspectionStatus.REPORT_DRAFT) {
      inspection.reopenFindings();
      inspections.saveAndFlush(inspection);
    }
    return toResponse(evidence.saveAndFlush(record));
  }

  /**
   * MF3-02: bulk retirement of captures. Applies a single shared retirement reason across the
   * target captures without deleting their stored bytes, checksums, or audit rows.
   *
   * <p>If {@code evidenceIds} is empty or null, all unretired captures for the inspection are
   * retired.
   */
  @Transactional
  public List<EvidenceResponse> bulkRetire(
      UUID actorId, UUID inspectionId, List<UUID> evidenceIds, String reason) {
    Inspection inspection = requireAssignedInspection(actorId, inspectionId);
    requireEvidenceEditable(inspectionId, inspection);
    if (reason == null || reason.isBlank()) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "EVIDENCE_RETIRE_INVALID", "A retirement reason is required");
    }
    List<Evidence> activeCaptures =
        evidence.findByInspectionIdAndRetiredAtIsNullOrderByCreatedAtAsc(inspectionId);
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
      target.retire(actorId, reason);
    }
    List<Evidence> saved = evidence.saveAllAndFlush(targets);
    if (inspection.getStatus() == InspectionStatus.REPORT_DRAFT) {
      inspection.reopenFindings();
      inspections.saveAndFlush(inspection);
    }
    return saved.stream().map(this::toResponse).toList();
  }

  private void requireEvidenceEditable(UUID inspectionId, Inspection inspection) {
    if (inspection.getStatus() == InspectionStatus.REPORT_PUBLISHED
        || inspection.getStatus() == InspectionStatus.REPAIR_PENDING
        || inspection.getStatus() == InspectionStatus.COMPLETED) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "INSPECTION_STATE_CONFLICT",
          "Evidence is immutable after publication");
    }
    InspectionReport report = reports.findByInspectionId(inspectionId).orElse(null);
    if (report == null) return;
    if (ReportStatus.SUBMITTED.name().equals(report.getStatus())
        || ReportStatus.APPROVED.name().equals(report.getStatus())
        || ReportStatus.PUBLISHED.name().equals(report.getStatus())) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "EVIDENCE_LOCKED",
          "Withdraw the submitted report or ask the reviewer to return it before changing evidence");
    }
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
    return new EvidencePageResponse(
        result.getContent().stream().map(this::toResponse).toList(),
        safePage,
        safePageSize,
        result.getTotalElements(),
        result.getTotalPages(),
        evidence.countByInspectionIdAndRetiredAtIsNull(inspectionId),
        evidence.countByInspectionIdAndRetiredAtIsNotNull(inspectionId),
        summary.getLatestCreatedAt(),
        summary.getLatestRetiredAt());
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
        case ACTIVE -> predicates.add(builder.isNull(root.get("retiredAt")));
        case RETIRED -> predicates.add(builder.isNotNull(root.get("retiredAt")));
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
    if (record.isRetired()) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "EVIDENCE_RETIRED", "Retired evidence cannot be analysed");
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

  /**
   * Whether image bytes actually decode. {@code ImageIO.read} returns null when no registered
   * reader recognises the content, and throws when the header is valid but the data is truncated,
   * so both outcomes mean the evidence is not usable.
   */
  private static boolean isDecodableImage(byte[] content) {
    try {
      return ImageIO.read(new ByteArrayInputStream(content)) != null;
    } catch (IOException | RuntimeException ex) {
      return false;
    }
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
        record.getCreatedAt(),
        record.getRetiredByUserId(),
        record.getRetiredAt(),
        record.getRetirementReason(),
        record.getLocationDescription(),
        false);
  }

  private EvidenceResponse toResponse(Evidence record, boolean deduplicated) {
    EvidenceResponse response = toResponse(record);
    return new EvidenceResponse(
        response.id(),
        response.inspectionId(),
        response.fieldSessionId(),
        response.fileName(),
        response.contentType(),
        response.sizeBytes(),
        response.checksumSha256(),
        response.captureTime(),
        response.source(),
        response.latitude(),
        response.longitude(),
        response.externalReference(),
        response.uploadStatus(),
        response.createdAt(),
        response.retiredByUserId(),
        response.retiredAt(),
        response.retirementReason(),
        response.locationDescription(),
        deduplicated);
  }

  public record EvidenceContent(String contentType, String fileName, InputStream stream) {}
}
