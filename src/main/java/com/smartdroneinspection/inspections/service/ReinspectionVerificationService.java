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
import com.smartdroneinspection.shared.auth.Roles;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.users.UserAccess;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
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

  public ReinspectionVerificationService(
      ReinspectionDefectVerificationRepository verifications,
      InspectionRepository inspections,
      FindingRepository findings,
      EvidenceRepository evidences,
      InspectionReportRepository reports,
      UserAccess userAccess) {
    this.verifications = verifications;
    this.inspections = inspections;
    this.findings = findings;
    this.evidences = evidences;
    this.reports = reports;
    this.userAccess = userAccess;
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

    reports
        .findFirstByInspectionIdOrderByVersionNoDesc(inspectionId)
        .ifPresent(
            report -> {
              if (report.getStatus() == ReportStatus.SUBMITTED
                  || report.getStatus() == ReportStatus.APPROVED
                  || report.getStatus() == ReportStatus.PUBLISHED) {
                throw new BusinessException(
                    HttpStatus.CONFLICT,
                    "REPORT_LOCKED",
                    "Defect verifications are locked while the report is submitted, approved, or published");
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

    Evidence newEvidence = null;
    if (request.newEvidenceId() != null) {
      newEvidence =
          evidences
              .findByIdAndInspectionId(request.newEvidenceId(), inspectionId)
              .orElseThrow(
                  () ->
                      new BusinessException(
                          HttpStatus.BAD_REQUEST,
                          "INVALID_EVIDENCE",
                          "Evidence must belong to this re-inspection"));
    }

    verification.updateVerification(status, request.newEvidenceId(), request.inspectorNotes());
    ReinspectionDefectVerification saved = verifications.saveAndFlush(verification);

    Finding sourceFinding = findings.findById(saved.getSourceFindingId()).orElse(null);
    Evidence sourceEvidence =
        sourceFinding != null && sourceFinding.getEvidenceId() != null
            ? evidences.findById(sourceFinding.getEvidenceId()).orElse(null)
            : null;

    return toResponse(saved, sourceFinding, newEvidence, sourceEvidence);
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
        item.getNewEvidenceId(),
        newEvidence != null ? newEvidence.getObjectKey() : null,
        item.getResolutionStatus().name(),
        item.getInspectorNotes(),
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
}
