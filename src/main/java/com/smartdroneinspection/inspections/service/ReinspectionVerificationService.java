package com.smartdroneinspection.inspections.service;

import com.smartdroneinspection.inspections.api.dto.request.UpdateDefectVerificationRequest;
import com.smartdroneinspection.inspections.api.dto.response.ReinspectionVerificationResponse;
import com.smartdroneinspection.inspections.domain.Evidence;
import com.smartdroneinspection.inspections.domain.FieldSession;
import com.smartdroneinspection.inspections.domain.Finding;
import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.domain.ReinspectionDefectVerification;
import com.smartdroneinspection.inspections.domain.enums.DefectResolutionStatus;
import com.smartdroneinspection.inspections.domain.enums.FindingStatus;
import com.smartdroneinspection.inspections.domain.enums.InspectionStatus;
import com.smartdroneinspection.inspections.domain.enums.ReportStatus;
import com.smartdroneinspection.inspections.repository.EvidenceRepository;
import com.smartdroneinspection.inspections.repository.FieldSessionRepository;
import com.smartdroneinspection.inspections.repository.FindingRepository;
import com.smartdroneinspection.inspections.repository.InspectionReportRepository;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.inspections.repository.ReinspectionDefectVerificationRepository;
import com.smartdroneinspection.shared.auth.Roles;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.users.UserAccess;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@EnableConfigurationProperties(ReinspectionProperties.class)
public class ReinspectionVerificationService {

  private final ReinspectionDefectVerificationRepository verifications;
  private final InspectionRepository inspections;
  private final FindingRepository findings;
  private final EvidenceRepository evidences;
  private final FieldSessionRepository fieldSessions;
  private final InspectionReportRepository reports;
  private final UserAccess userAccess;
  private final ReinspectionProperties reinspectionProperties;

  public ReinspectionVerificationService(
      ReinspectionDefectVerificationRepository verifications,
      InspectionRepository inspections,
      FindingRepository findings,
      EvidenceRepository evidences,
      InspectionReportRepository reports,
      UserAccess userAccess) {
    this(verifications, inspections, findings, evidences, null, reports, userAccess, null);
  }

  @org.springframework.beans.factory.annotation.Autowired
  public ReinspectionVerificationService(
      ReinspectionDefectVerificationRepository verifications,
      InspectionRepository inspections,
      FindingRepository findings,
      EvidenceRepository evidences,
      FieldSessionRepository fieldSessions,
      InspectionReportRepository reports,
      UserAccess userAccess,
      ReinspectionProperties reinspectionProperties) {
    this.verifications = verifications;
    this.inspections = inspections;
    this.findings = findings;
    this.evidences = evidences;
    this.fieldSessions = fieldSessions;
    this.reports = reports;
    this.userAccess = userAccess;
    this.reinspectionProperties =
        reinspectionProperties != null
            ? reinspectionProperties
            : new ReinspectionProperties(java.time.Duration.ofDays(90));
  }

  /** Seeds verification rows from all active confirmed findings of the source inspection. */
  @Transactional
  public int initializeVerificationsForReinspection(UUID reinspectionId, UUID sourceInspectionId) {
    if (reinspectionId == null || sourceInspectionId == null) {
      return 0;
    }
    if (!verifications.findByReinspectionIdOrderByCreatedAtAsc(reinspectionId).isEmpty()) {
      return 0;
    }
    List<Finding> confirmedFindings =
        findings.findByInspectionIdOrderByCreatedAtAsc(sourceInspectionId).stream()
            .filter(f -> f.getStatus() == FindingStatus.CONFIRMED && !f.isDeleted())
            .toList();
    if (confirmedFindings.isEmpty()) {
      return 0;
    }
    for (Finding finding : confirmedFindings) {
      verifications.save(new ReinspectionDefectVerification(reinspectionId, finding.getId()));
    }
    verifications.flush();
    return confirmedFindings.size();
  }

  @Transactional
  public List<ReinspectionVerificationResponse> getVerifications(UUID actorId, UUID inspectionId) {
    Inspection inspection = requireInspection(actorId, inspectionId);
    List<ReinspectionDefectVerification> items =
        verifications.findByReinspectionIdOrderByCreatedAtAsc(inspection.getId());
    if (items.isEmpty()
        && inspection.isReinspection()
        && inspection.getSourceInspectionId() != null) {
      initializeVerificationsForReinspection(
          inspection.getId(), inspection.getSourceInspectionId());
      items = verifications.findByReinspectionIdOrderByCreatedAtAsc(inspection.getId());
    }
    if (items.isEmpty()) {
      return List.of();
    }

    List<UUID> findingIds =
        items.stream().map(ReinspectionDefectVerification::getSourceFindingId).toList();
    Map<UUID, Finding> findingMap =
        findings.findAllById(findingIds).stream()
            .collect(Collectors.toMap(Finding::getId, Function.identity()));

    List<UUID> evidenceIds =
        items.stream()
            .map(ReinspectionDefectVerification::getNewEvidenceId)
            .filter(Objects::nonNull)
            .toList();
    Map<UUID, Evidence> newEvidenceMap =
        evidences.findAllById(evidenceIds).stream()
            .collect(Collectors.toMap(Evidence::getId, Function.identity()));

    List<UUID> sourceEvidenceIds =
        findingMap.values().stream().map(Finding::getEvidenceId).filter(Objects::nonNull).toList();
    Map<UUID, Evidence> sourceEvidenceMap =
        evidences.findAllById(sourceEvidenceIds).stream()
            .collect(Collectors.toMap(Evidence::getId, Function.identity()));

    return items.stream()
        .map(
            item ->
                toResponse(
                    item,
                    findingMap.get(item.getSourceFindingId()),
                    newEvidenceMap.get(item.getNewEvidenceId()),
                    sourceEvidenceMap))
        .toList();
  }

  @Transactional
  public ReinspectionVerificationResponse updateVerification(
      UUID actorId,
      UUID inspectionId,
      UUID verificationId,
      UpdateDefectVerificationRequest request) {
    Inspection inspection = requireInspection(actorId, inspectionId);
    requireCanEdit(actorId, inspection);

    if (inspection.getStatus() == InspectionStatus.SCHEDULED
        || inspection.getStatus() == InspectionStatus.IN_PROGRESS) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "FIELD_SESSION_NOT_COMPLETED",
          "Complete the on-site field session before recording defect verifications");
    }

    reports
        .findFirstByInspectionIdOrderByVersionNoDesc(inspectionId)
        .ifPresent(
            report -> {
              if (report.getStatus() == ReportStatus.SUBMITTED
                  || report.getStatus() == ReportStatus.PUBLISHED) {
                throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "REPORT_LOCKED",
                    "Defect verifications are locked while the report is submitted or published");
              }
            });

    ReinspectionDefectVerification verification =
        verifications
            .findById(verificationId)
            .filter(v -> v.getReinspectionId().equals(inspectionId))
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND, "NOT_FOUND", "Verification item not found"));

    DefectResolutionStatus status;
    try {
      status = DefectResolutionStatus.valueOf(request.resolutionStatus().trim().toUpperCase());
    } catch (IllegalArgumentException | NullPointerException e) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "INVALID_STATUS",
          "Invalid resolution status: " + request.resolutionStatus());
    }

    if (status == DefectResolutionStatus.RESOLVED && request.newEvidenceId() == null) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "EVIDENCE_REQUIRED_FOR_RESOLVED",
          "A fresh re-inspection evidence capture is required to mark a defect as RESOLVED");
    }

    Evidence newEvidence = null;
    if (request.newEvidenceId() != null) {
      newEvidence =
          evidences
              .findByIdAndInspectionId(request.newEvidenceId(), inspectionId)
              .filter(ev -> !ev.isDeleted())
              .orElseThrow(
                  () ->
                      new BusinessException(
                          HttpStatus.BAD_REQUEST,
                          "INVALID_EVIDENCE",
                          "Evidence must belong to this re-inspection"));
      if (status == DefectResolutionStatus.RESOLVED) {
        requireFreshEvidence(inspection, newEvidence, verification.getSourceFindingId());
      }
    }

    verification.updateVerification(
        status,
        request.newEvidenceId(),
        request.inspectorNotes(),
        reinspectionProperties.resolvedValidity());
    ReinspectionDefectVerification saved = verifications.saveAndFlush(verification);

    if (inspection.getStatus() == InspectionStatus.FIELD_COMPLETED) {
      List<ReinspectionDefectVerification> allItems =
          verifications.findByReinspectionIdOrderByCreatedAtAsc(inspectionId);
      boolean allEvaluated =
          !allItems.isEmpty()
              && allItems.stream().allMatch(ReinspectionVerificationService::isEvaluated);
      boolean hasEvidence =
          request.newEvidenceId() != null
              || evidences.existsByInspectionIdAndIsDeletedFalse(inspectionId);
      long pendingAi =
          findings.findByInspectionIdOrderByCreatedAtAsc(inspectionId).stream()
              .filter(f -> f.getStatus() == FindingStatus.PENDING && f.getModelName() != null)
              .count();
      if (allEvaluated && hasEvidence && pendingAi == 0) {
        inspection.confirmFindings();
        inspections.saveAndFlush(inspection);
      } else if (inspection.hasFindingsConfirmation()) {
        inspection.reopenFindings();
        inspections.saveAndFlush(inspection);
      }
    }

    Finding sourceFinding = findings.findById(saved.getSourceFindingId()).orElse(null);
    Evidence sourceEvidence =
        sourceFinding != null && sourceFinding.getEvidenceId() != null
            ? evidences.findById(sourceFinding.getEvidenceId()).orElse(null)
            : null;

    return toResponse(saved, sourceFinding, newEvidence, sourceEvidence);
  }

  /**
   * Rejects an expired RESOLVED verdict. A RESOLVED verdict is only authoritative for its validity
   * window; past it the defect must be re-verified before the report is published as completion.
   */
  public void requireNoExpiredResolutions(UUID reinspectionId) {
    long expired =
        verifications.findByReinspectionIdOrderByCreatedAtAsc(reinspectionId).stream()
            .filter(item -> item.isResolutionExpired(java.time.Instant.now()))
            .count();
    if (expired > 0) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "RESOLUTION_EXPIRED",
          "A RESOLVED verdict has expired and must be re-verified before publishing this "
              + "re-inspection report ("
              + expired
              + " defect(s) expired)");
    }
  }

  private ReinspectionVerificationResponse toResponse(
      ReinspectionDefectVerification item,
      Finding sourceFinding,
      Evidence newEvidence,
      Map<UUID, Evidence> sourceEvidenceMap) {
    Evidence sourceEvidence =
        sourceFinding != null && sourceFinding.getEvidenceId() != null
            ? sourceEvidenceMap.get(sourceFinding.getEvidenceId())
            : null;
    return toResponse(item, sourceFinding, newEvidence, sourceEvidence);
  }

  private ReinspectionVerificationResponse toResponse(
      ReinspectionDefectVerification item,
      Finding sourceFinding,
      Evidence newEvidence,
      Evidence sourceEvidence) {
    return new ReinspectionVerificationResponse(
        item.getId(),
        item.getReinspectionId(),
        item.getSourceFindingId(),
        sourceFinding != null ? sourceFinding.getFindingCode() : null,
        sourceFinding != null ? sourceFinding.getDefectLabel() : null,
        sourceFinding != null && sourceFinding.getSeverity() != null
            ? sourceFinding.getSeverity().name()
            : null,
        sourceFinding != null ? sourceFinding.getTechnicalNotes() : null,
        sourceFinding != null ? sourceFinding.getEvidenceId() : null,
        sourceEvidence != null ? sourceEvidence.getObjectKey() : null,
        sourceEvidence != null ? sourceEvidence.getLocationDescription() : null,
        sourceEvidence != null ? sourceEvidence.getLatitude() : null,
        sourceEvidence != null ? sourceEvidence.getLongitude() : null,
        item.getNewEvidenceId(),
        newEvidence != null ? newEvidence.getObjectKey() : null,
        item.getResolutionStatus().name(),
        item.getInspectorNotes(),
        item.getVerifiedAt(),
        item.getValidUntil(),
        item.getCreatedAt(),
        item.getUpdatedAt());
  }

  private Inspection requireInspection(UUID actorId, UUID inspectionId) {
    UserAccess.ActiveUser actor =
        userAccess
            .findActiveUser(actorId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Actor not found"));

    Inspection inspection =
        inspections
            .findById(inspectionId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND, "NOT_FOUND", "Inspection not found"));

    if (actor.hasRole(Roles.ADMIN) || actor.hasRole(Roles.MANAGER)) {
      return inspection;
    }

    if (actor.ownerCustomerId() != null
        && actor.ownerCustomerId().equals(inspection.getOwnerCustomerId())) {
      return inspection;
    }

    if (inspection.isInspectedBy(actorId)) {
      return inspection;
    }

    throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "No access to this inspection");
  }

  private void requireCanEdit(UUID actorId, Inspection inspection) {
    UserAccess.ActiveUser actor =
        userAccess
            .findActiveUser(actorId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Actor not found"));

    boolean isAssignedInspector = inspection.isInspectedBy(actorId);
    boolean isManagerOrAdmin = actor.hasRole(Roles.ADMIN) || actor.hasRole(Roles.MANAGER);

    if (!isAssignedInspector && !isManagerOrAdmin) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN,
          "FORBIDDEN",
          "Only the assigned inspector or manager can update defect verifications");
    }
  }

  /**
   * Guards against replaying an old or unrelated capture as proof a defect was re-fixed.
   *
   * <p>A RESOLVED verdict is only trustworthy if the evidence was captured during this
   * re-inspection's field session, inside that session's flight window, and is not a byte-for-byte
   * reuse of the original inspection evidence. Each failed check raises a distinct error so the
   * Inspector sees exactly why the capture was refused.
   */
  private void requireFreshEvidence(
      Inspection inspection, Evidence newEvidence, UUID sourceFindingId) {
    UUID fieldSessionId = newEvidence.getFieldSessionId();
    if (fieldSessionId == null) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "EVIDENCE_NOT_FRESH",
          "Re-inspection evidence must be captured within a field session of this re-inspection");
    }
    FieldSession session =
        fieldSessions
            .findByIdAndInspectionId(fieldSessionId, inspection.getId())
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.BAD_REQUEST,
                        "EVIDENCE_SESSION_MISMATCH",
                        "Re-inspection evidence must belong to a field session of this re-inspection"));

    if (!FieldSession.FIELD_COMPLETED.equals(session.getStatus())) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "EVIDENCE_SESSION_NOT_COMPLETED",
          "Re-inspection evidence must come from a completed field session");
    }

    Instant captureTime = newEvidence.getCaptureTime();
    Instant sessionStart = session.getStartedAt();
    Instant sessionEnd = session.getEndedAt();
    if (captureTime == null || sessionStart == null || sessionEnd == null) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "EVIDENCE_CAPTURE_TIME_REQUIRED",
          "Re-inspection evidence must record its capture time and the field session must have started");
    }
    if (captureTime.isBefore(sessionStart)
        || (sessionEnd != null && captureTime.isAfter(sessionEnd))) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "EVIDENCE_OUTSIDE_SESSION",
          "Re-inspection evidence capture time must fall within the field session flight window");
    }

    String objectKey = newEvidence.getObjectKey();
    if (objectKey == null || objectKey.isBlank()) {
      return;
    }
    UUID sourceInspectionId = inspection.getSourceInspectionId();
    if (sourceInspectionId != null) {
      boolean reusedSourceEvidence =
          evidences
              .findByInspectionIdAndIsDeletedFalseOrderByCreatedAtAsc(sourceInspectionId)
              .stream()
              .anyMatch(
                  sourceEvidence ->
                      sourceEvidence.getId().equals(newEvidence.getId())
                          || objectKey.equals(sourceEvidence.getObjectKey()));
      if (reusedSourceEvidence) {
        throw new BusinessException(
            HttpStatus.BAD_REQUEST,
            "EVIDENCE_REUSED",
            "Re-inspection evidence must be a fresh capture, not a reuse of evidence from the original inspection");
      }
    } else if (sourceFindingId != null) {
      // Defensive fallback for legacy re-inspections without sourceInspectionId metadata.
      Finding sourceFinding = findings.findById(sourceFindingId).orElse(null);
      UUID sourceEvidenceId = sourceFinding != null ? sourceFinding.getEvidenceId() : null;
      if (sourceEvidenceId != null && sourceEvidenceId.equals(newEvidence.getId())) {
        throw new BusinessException(
            HttpStatus.BAD_REQUEST,
            "EVIDENCE_REUSED",
            "Re-inspection evidence must be a fresh capture, not the original inspection evidence");
      }
      if (sourceEvidenceId != null) {
        Evidence sourceEvidence = evidences.findById(sourceEvidenceId).orElse(null);
        if (sourceEvidence != null && objectKey.equals(sourceEvidence.getObjectKey())) {
          throw new BusinessException(
              HttpStatus.BAD_REQUEST,
              "EVIDENCE_REUSED",
              "Re-inspection evidence must be a fresh capture, not a reuse of the original inspection evidence");
        }
      }
    }
  }

  public static boolean isEvaluated(ReinspectionDefectVerification item) {
    if (item == null || item.getResolutionStatus() == null) {
      return false;
    }
    if (item.getResolutionStatus() == DefectResolutionStatus.RESOLVED) {
      return item.getNewEvidenceId() != null;
    }
    return item.getNewEvidenceId() != null
        || (item.getInspectorNotes() != null && !item.getInspectorNotes().isBlank());
  }
}
