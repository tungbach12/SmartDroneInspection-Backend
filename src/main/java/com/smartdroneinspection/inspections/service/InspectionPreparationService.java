package com.smartdroneinspection.inspections.service;

import com.smartdroneinspection.inspections.api.dto.request.PrepareShotListRequest;
import com.smartdroneinspection.inspections.api.dto.request.SubmitPreparationRequest;
import com.smartdroneinspection.inspections.api.dto.response.InspectionPreparationResponse;
import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.domain.InspectionPreparation;
import com.smartdroneinspection.inspections.domain.enums.InspectionPreparationStatus;
import com.smartdroneinspection.inspections.repository.InspectionPreparationRepository;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.shared.auth.Roles;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.users.UserAccess;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * MF2-03 and MF2-06: the assigned inspector drafts a preparation and submits it for review.
 *
 * <p>Drafting is not review and submitting is not readiness. MF2-07 records a separate decision by
 * a named reviewer, and nothing here moves an inspection toward flight.
 *
 * <p>Scope comes from the token's organization and is re-checked against the inspection that owns
 * the preparation. {@code inspection_preparations} stores no organization of its own, so the only
 * thing separating two tenants' work is that join - which is why every lookup here filters on it
 * rather than trusting a preparation id supplied by the caller.
 */
@Service
public class InspectionPreparationService {

  private final InspectionPreparationRepository preparations;
  private final InspectionRepository inspections;
  private final UserAccess userAccess;

  public InspectionPreparationService(
      InspectionPreparationRepository preparations,
      InspectionRepository inspections,
      UserAccess userAccess) {
    this.preparations = preparations;
    this.inspections = inspections;
    this.userAccess = userAccess;
  }

  /**
   * Records the component shot-list, evidence types, access limits and known hazards (MF2-03).
   *
   * <p>Each call produces the first version of an inspection's preparation, or revises the current
   * draft if one is open. Versions are never edited after submission, so calling this on a
   * submitted preparation is refused rather than quietly starting a competing version.
   */
  @Transactional
  public InspectionPreparationResponse prepareShotList(
      UUID inspectorId, UUID inspectionId, PrepareShotListRequest request) {
    UserAccess.ActiveUser inspector = requireInspector(inspectorId);
    Inspection inspection = requireScopedInspection(inspectionId, inspector.organizationId());

    if (!inspection.isInspectedBy(inspectorId)) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN,
          "PREPARATION_SCOPE_DENIED",
          "This inspection is not assigned to you.");
    }

    InspectionPreparation preparation =
        openDraft(inspectionId, inspectorId)
            .orElseGet(() -> startFirstVersion(inspection, inspectorId));

    try {
      preparation.recordShotList(request.shotList());
      preparation.recordEvidenceTypes(request.evidenceTypes());
      preparation.recordAccessConstraints(request.accessConstraints());
      preparation.recordSafetyObservations(request.safetyObservations());
    } catch (IllegalStateException exception) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "PREPARATION_NOT_EDITABLE", exception.getMessage());
    }

    return toResponse(preparations.saveAndFlush(preparation));
  }

  /**
   * Attributable submission of the preparation (MF2-06).
   *
   * <p>The completeness rules live on the entity, so an incomplete preparation is refused the same
   * way whether it arrives through HTTP, a batch import or a future endpoint.
   */
  @Transactional
  public InspectionPreparationResponse submitPreparation(
      UUID inspectorId, UUID preparationId, SubmitPreparationRequest request) {
    UserAccess.ActiveUser inspector = requireInspector(inspectorId);
    UUID organizationId = requireOrganization(inspector);

    InspectionPreparation preparation =
        preparations
            .findByIdAndOrganizationId(preparationId, organizationId)
            .orElseThrow(InspectionPreparationService::preparationNotFound);

    if (!preparation.getInspectorUserId().equals(inspectorId)) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN,
          "PREPARATION_SCOPE_DENIED",
          "This preparation belongs to another inspector.");
    }

    if (!preparation.isEditable()) {
      throw new BusinessException(
          HttpStatus.CONFLICT,
          "PREPARATION_NOT_EDITABLE",
          "This preparation is no longer open for submission.");
    }

    try {
      preparation.submit(inspectorId);
    } catch (IllegalStateException | IllegalArgumentException exception) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "PREPARATION_INCOMPLETE", exception.getMessage());
    }

    return toResponse(preparations.saveAndFlush(preparation));
  }

  /** The preparation history of an inspection, newest version first. */
  @Transactional(readOnly = true)
  public List<InspectionPreparationResponse> listPreparations(UUID inspectorId, UUID inspectionId) {
    UserAccess.ActiveUser inspector = requireInspector(inspectorId);
    requireScopedInspection(inspectionId, requireOrganization(inspector));

    return preparations.findByInspectionIdOrderByPreparationVersionDesc(inspectionId).stream()
        .map(this::toResponse)
        .toList();
  }

  /** One preparation version, scoped through its inspection. */
  @Transactional(readOnly = true)
  public InspectionPreparationResponse getPreparation(UUID inspectorId, UUID preparationId) {
    UserAccess.ActiveUser inspector = requireInspector(inspectorId);

    return toResponse(
        preparations
            .findByIdAndOrganizationId(preparationId, requireOrganization(inspector))
            .orElseThrow(InspectionPreparationService::preparationNotFound));
  }

  /**
   * The draft an inspector may keep editing, if one is open.
   *
   * <p>Only a DRAFT qualifies. A returned version has to be reopened through review rework rather
   * than silently absorbed into a new edit, otherwise the record of what was sent back is lost.
   */
  private java.util.Optional<InspectionPreparation> openDraft(UUID inspectionId, UUID inspectorId) {
    return preparations.findByInspectionIdOrderByPreparationVersionDesc(inspectionId).stream()
        .filter(preparation -> preparation.getInspectorUserId().equals(inspectorId))
        .filter(preparation -> preparation.getStatus() == InspectionPreparationStatus.DRAFT)
        .findFirst();
  }

  private InspectionPreparation startFirstVersion(Inspection inspection, UUID inspectorId) {
    return new InspectionPreparation(
        inspection.getId(), inspectorId, nextVersion(inspection.getId()));
  }

  private int nextVersion(UUID inspectionId) {
    return preparations
        .findFirstByInspectionIdOrderByPreparationVersionDesc(inspectionId)
        .map(preparation -> preparation.getPreparationVersion() + 1)
        .orElse(1);
  }

  private Inspection requireScopedInspection(UUID inspectionId, UUID organizationId) {
    return inspections
        .findByIdAndOrganizationId(inspectionId, organizationId)
        .orElseThrow(
            () ->
                new BusinessException(
                    HttpStatus.NOT_FOUND, "INSPECTION_NOT_FOUND", "Inspection was not found."));
  }

  private InspectionPreparationResponse toResponse(InspectionPreparation preparation) {
    return new InspectionPreparationResponse(
        preparation.getId(),
        preparation.getInspectionId(),
        preparation.getInspectorUserId(),
        preparation.getPreparationVersion(),
        preparation.getShotList(),
        preparation.getEvidenceTypes(),
        preparation.getAccessConstraints(),
        preparation.getSafetyObservations(),
        preparation.getPermitDocumentReferences(),
        preparation.getStatus(),
        preparation.getSubmittedAt(),
        preparation.getCreatedAt(),
        preparation.getUpdatedAt());
  }

  private UserAccess.ActiveUser requireInspector(UUID inspectorId) {
    UserAccess.ActiveUser actor =
        userAccess
            .findActiveUser(inspectorId)
            .orElseThrow(
                () ->
                    new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "User is not active"));
    if (!actor.hasRole(Roles.INSPECTOR)) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN, "FORBIDDEN", "Role " + Roles.INSPECTOR + " is required");
    }
    return actor;
  }

  private UUID requireOrganization(UserAccess.ActiveUser actor) {
    if (actor.organizationId() == null) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN, "FORBIDDEN", "User has no organization scope");
    }
    return actor.organizationId();
  }

  private static BusinessException preparationNotFound() {
    return new BusinessException(
        HttpStatus.NOT_FOUND, "PREPARATION_NOT_FOUND", "Preparation was not found.");
  }
}
