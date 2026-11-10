package com.smartdroneinspection.inspections.service;

import com.smartdroneinspection.inspections.api.dto.request.UpdateDefectVerificationRequest;
import com.smartdroneinspection.inspections.api.dto.response.ReinspectionVerificationResponse;
import com.smartdroneinspection.inspections.domain.Evidence;
import com.smartdroneinspection.inspections.domain.Finding;
import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.domain.ReinspectionDefectVerification;
import com.smartdroneinspection.inspections.domain.enums.DefectResolutionStatus;
import com.smartdroneinspection.inspections.domain.enums.ReportStatus;
import com.smartdroneinspection.inspections.repository.EvidenceRepository;
import com.smartdroneinspection.inspections.repository.FindingRepository;
import com.smartdroneinspection.inspections.repository.InspectionReportRepository;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.inspections.repository.ReinspectionDefectVerificationRepository;
import com.smartdroneinspection.shared.api.RepairKnowledgeLookup;
import com.smartdroneinspection.shared.auth.Roles;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.users.UserAccess;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReinspectionVerificationService {

  private final ReinspectionDefectVerificationRepository verifications;
  private final InspectionRepository inspections;
  private final FindingRepository findings;
  private final EvidenceRepository evidences;
  private final InspectionReportRepository reports;
  private final UserAccess userAccess;
  private final Optional<RepairKnowledgeLookup> knowledgeLookup;

  public ReinspectionVerificationService(
      ReinspectionDefectVerificationRepository verifications,
      InspectionRepository inspections,
      FindingRepository findings,
      EvidenceRepository evidences,
      InspectionReportRepository reports,
      UserAccess userAccess) {
    this(verifications, inspections, findings, evidences, reports, userAccess, Optional.empty());
  }

  @Autowired
  public ReinspectionVerificationService(
      ReinspectionDefectVerificationRepository verifications,
      InspectionRepository inspections,
      FindingRepository findings,
      EvidenceRepository evidences,
      InspectionReportRepository reports,
      UserAccess userAccess,
      Optional<RepairKnowledgeLookup> knowledgeLookup) {
    this.verifications = verifications;
    this.inspections = inspections;
    this.findings = findings;
    this.evidences = evidences;
    this.reports = reports;
    this.userAccess = userAccess;
    this.knowledgeLookup = knowledgeLookup != null ? knowledgeLookup : Optional.empty();
  }

  @Transactional(readOnly = true)
  public List<ReinspectionVerificationResponse> getVerifications(UUID actorId, UUID inspectionId) {
    Inspection inspection = requireInspection(actorId, inspectionId);
    List<ReinspectionDefectVerification> items =
        verifications.findByReinspectionId(inspection.getId());
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

    if (inspection.getStatus()
            == com.smartdroneinspection.inspections.domain.enums.InspectionStatus.SCHEDULED
        || inspection.getStatus()
            == com.smartdroneinspection.inspections.domain.enums.InspectionStatus.IN_PROGRESS) {
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
    }

    verification.updateVerification(status, request.newEvidenceId(), request.inspectorNotes());
    ReinspectionDefectVerification saved = verifications.saveAndFlush(verification);

    if (inspection.getStatus()
        == com.smartdroneinspection.inspections.domain.enums.InspectionStatus.FIELD_COMPLETED) {
      List<ReinspectionDefectVerification> allItems =
          verifications.findByReinspectionId(inspectionId);
      boolean allEvaluated =
          !allItems.isEmpty()
              && allItems.stream().allMatch(ReinspectionVerificationService::isEvaluated);
      boolean hasEvidence =
          request.newEvidenceId() != null
              || evidences.existsByInspectionIdAndIsDeletedFalse(inspectionId);
      long pendingAi =
          findings.findByInspectionIdOrderByCreatedAtAsc(inspectionId).stream()
              .filter(
                  f ->
                      f.getStatus()
                              == com.smartdroneinspection.inspections.domain.enums.FindingStatus
                                  .PENDING
                          && f.getModelName() != null)
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
   * Spawns a linked RE_INSPECTION directly from a published inspection report when the Customer has
   * completed repair work and requests post-repair verification.
   */
  @Transactional
  public com.smartdroneinspection.inspections.api.dto.response.InspectionResponse
      createDirectReinspection(
          UUID actorId,
          UUID sourceInspectionId,
          String contractorRepairNotes,
          String repairProofUrl) {
    Inspection sourceInspection = requireInspection(actorId, sourceInspectionId);
    List<Finding> confirmedFindings =
        findings.findByInspectionIdOrderByCreatedAtAsc(sourceInspectionId).stream()
            .filter(
                f ->
                    f.getStatus()
                            == com.smartdroneinspection.inspections.domain.enums.FindingStatus
                                .CONFIRMED
                        && !f.isDeleted())
            .toList();
    if (confirmedFindings.isEmpty()) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "NO_CONFIRMED_FINDINGS",
          "At least one confirmed defect is required to schedule a re-inspection");
    }

    UUID latestPublishedReportVersionId =
        reports
            .findFirstByInspectionIdAndStatus(sourceInspectionId, ReportStatus.PUBLISHED)
            .map(com.smartdroneinspection.inspections.domain.InspectionReport::getId)
            .orElse(null);

    Inspection reinspection =
        Inspection.createReinspection(
            sourceInspection.getOrganizationId(),
            sourceInspection.getAssetId(),
            sourceInspection.getInspectorId(),
            sourceInspection.getDroneId(),
            java.time.Instant.now().plusSeconds(3600),
            null,
            sourceInspection.getId(),
            latestPublishedReportVersionId);
    reinspection = inspections.saveAndFlush(reinspection);

    String handoverNotes =
        contractorRepairNotes != null && !contractorRepairNotes.isBlank()
            ? contractorRepairNotes.trim()
            : "Khách hàng đã hoàn tất sửa chữa khắc phục và yêu cầu bay tái kiểm định nghiệm thu.";

    for (Finding finding : confirmedFindings) {
      ReinspectionDefectVerification verification =
          new ReinspectionDefectVerification(reinspection.getId(), finding.getId());
      verification.setRepairProof(
          repairProofUrl != null && !repairProofUrl.isBlank() ? repairProofUrl.trim() : null,
          handoverNotes);
      verifications.save(verification);
    }
    verifications.flush();

    return com.smartdroneinspection.inspections.api.dto.response.InspectionResponse.from(
        reinspection);
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
    String defectLabel = sourceFinding != null ? sourceFinding.getDefectLabel() : null;
    String technicalNotes = sourceFinding != null ? sourceFinding.getTechnicalNotes() : null;
    RepairKnowledgeLookup.VerificationGuidance guidance =
        knowledgeLookup
            .map(lookup -> lookup.lookupVerificationGuidance(defectLabel, technicalNotes))
            .orElse(null);
    return new ReinspectionVerificationResponse(
        item.getId(),
        item.getReinspectionId(),
        item.getSourceFindingId(),
        sourceFinding != null ? sourceFinding.getFindingCode() : null,
        defectLabel,
        sourceFinding != null && sourceFinding.getSeverity() != null
            ? sourceFinding.getSeverity().name()
            : null,
        technicalNotes,
        sourceFinding != null ? sourceFinding.getEvidenceId() : null,
        sourceEvidence != null ? sourceEvidence.getObjectKey() : null,
        sourceEvidence != null ? sourceEvidence.getLocationDescription() : null,
        sourceEvidence != null ? sourceEvidence.getLatitude() : null,
        sourceEvidence != null ? sourceEvidence.getLongitude() : null,
        item.getRepairProofUrl(),
        item.getRepairEngineerNotes(),
        item.getNewEvidenceId(),
        newEvidence != null ? newEvidence.getObjectKey() : null,
        item.getResolutionStatus().name(),
        item.getInspectorNotes(),
        guidance != null && guidance.referenceStandards() != null
            ? guidance.referenceStandards()
            : List.of(),
        guidance != null ? guidance.verificationChecklist() : null,
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

    if (actor.organizationId() != null
        && actor.organizationId().equals(inspection.getOrganizationId())) {
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
    boolean isManager =
        actor.hasRole(Roles.ADMIN)
            || actor.hasRole(Roles.MANAGER)
            || ((actor.hasRole(Roles.MANAGER) || actor.hasRole(Roles.ORG_MANAGER))
                && actor.organizationId() != null
                && actor.organizationId().equals(inspection.getOrganizationId()));
    boolean isAdmin = actor.hasRole(Roles.ADMIN);

    if (!isAssignedInspector && !isManager && !isAdmin) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN,
          "FORBIDDEN",
          "Only the assigned inspector or manager can update defect verifications");
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
