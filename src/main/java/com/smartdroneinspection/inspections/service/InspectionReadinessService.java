package com.smartdroneinspection.inspections.service;

import com.smartdroneinspection.assets.domain.Drone;
import com.smartdroneinspection.assets.domain.enums.DroneServiceability;
import com.smartdroneinspection.assets.readiness.AssetPairReadinessStatus;
import com.smartdroneinspection.assets.readiness.AssetPairReadinessSummary;
import com.smartdroneinspection.assets.readiness.AssignmentReadinessResponse;
import com.smartdroneinspection.assets.readiness.DroneDocumentReadinessAccess;
import com.smartdroneinspection.assets.readiness.DroneDocumentReadinessStatus;
import com.smartdroneinspection.assets.readiness.DroneDocumentSummary;
import com.smartdroneinspection.assets.repository.DroneRepository;
import com.smartdroneinspection.inspections.api.dto.response.ComplianceGateResponse;
import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.domain.InspectionPreparation;
import com.smartdroneinspection.inspections.domain.enums.InspectionPreparationStatus;
import com.smartdroneinspection.inspections.domain.enums.InspectionStatus;
import com.smartdroneinspection.inspections.repository.InspectionPreparationRepository;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.shared.auth.Roles;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.users.UserAccess;
import com.smartdroneinspection.workforce.credential.CredentialStatus;
import com.smartdroneinspection.workforce.credential.WorkforceCredentialAccess;
import com.smartdroneinspection.workforce.credential.WorkforceCredentialSummary;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns the independently reviewed MF2 readiness decision and its atomic state transitions. */
@Service
public class InspectionReadinessService {

  private final InspectionRepository inspections;
  private final InspectionPreparationRepository preparations;
  private final UserAccess userAccess;
  private final ComplianceGateService compliance;
  private final WorkforceCredentialAccess workforceCredentials;
  private final DroneDocumentReadinessAccess assetReadiness;
  private final DroneRepository drones;
  private final EntityManager entityManager;

  public InspectionReadinessService(
      InspectionRepository inspections,
      InspectionPreparationRepository preparations,
      UserAccess userAccess,
      ComplianceGateService compliance,
      WorkforceCredentialAccess workforceCredentials,
      DroneDocumentReadinessAccess assetReadiness,
      DroneRepository drones,
      EntityManager entityManager) {
    this.inspections = inspections;
    this.preparations = preparations;
    this.userAccess = userAccess;
    this.compliance = compliance;
    this.workforceCredentials = workforceCredentials;
    this.assetReadiness = assetReadiness;
    this.drones = drones;
    this.entityManager = entityManager;
  }

  @Transactional
  public InspectionPreparation approve(
      UUID reviewerId, UUID inspectionId, UUID preparationId, ReadinessReviewCommand command) {
    UserAccess.ActiveUser reviewer = requireOrgAdmin(reviewerId);
    Inspection inspection = lockInspection(inspectionId, requireOrganization(reviewer));
    requireReviewableInspection(inspection);
    requireIndependentReviewer(reviewerId, inspection);
    InspectionPreparation preparation = lockCurrentSubmittedPreparation(inspection, preparationId);
    requireNoCurrentApprovalForAnotherPreparation(inspection, preparation);
    Instant decidedAt = Instant.now();

    WorkforceCredentialSummary reviewerCredential =
        requireReviewerCredential(
            command.reviewerCredentialId(), reviewer.organizationId(), reviewerId, decidedAt);
    if (inspection.getPlannedStartAt() == null || inspection.getPlannedEndAt() == null) {
      throw readinessFailure("PLANNED_SESSION_MISSING", "Inspection has no planned window.");
    }
    requireCredentialValidAt(
        reviewerCredential, inspection.getPlannedStartAt(), "REVIEWER_CREDENTIAL_INVALID");
    if (!command.applicabilityComplete() || blank(command.applicabilityBasisReference())) {
      throw readinessFailure(
          "APPLICABILITY_ATTESTATION_REQUIRED",
          "A complete applicability attestation with a traceable basis is required.");
    }
    if (command.inspectorCredentialIds().isEmpty()
        && blank(command.noInspectorCredentialReason())) {
      throw readinessFailure(
          "INSPECTOR_CREDENTIAL_BASIS_REQUIRED",
          "An empty Inspector credential selection requires a documented basis.");
    }
    if (command.droneDocumentIds().isEmpty() && blank(command.noDroneDocumentReason())) {
      throw readinessFailure(
          "DRONE_DOCUMENT_BASIS_REQUIRED",
          "An empty Drone document selection requires a documented basis.");
    }

    resolveInspectorCredentials(
        command.inspectorCredentialIds(), reviewer.organizationId(), inspection);
    resolveDroneDocuments(command.droneDocumentIds(), reviewer.organizationId(), inspection);
    requirePair(reviewer.organizationId(), inspection);
    requireServiceableDrone(reviewer.organizationId(), inspection);
    ComplianceGateService.ComplianceGateEvaluation gate =
        compliance.evaluateReadiness(reviewerId, inspectionId);
    if (!gate.blockers().isEmpty()) {
      ComplianceGateResponse.Blocker first = gate.blockers().getFirst();
      throw readinessFailure(first.code(), first.detail());
    }
    if (gate.requiresHumanVerification() && blank(command.humanVerificationBasis())) {
      throw readinessFailure(
          "HUMAN_VERIFICATION_BASIS_REQUIRED",
          "Human-verification findings require an explicit traceable basis.");
    }

    InspectionPreparation lockedPreparation =
        preparations
            .findWithLockByIdAndInspectionId(preparationId, inspectionId)
            .orElseThrow(InspectionReadinessService::preparationNotFound);
    lockedPreparation.approve(reviewerId);
    inspection.approveReadiness();
    InspectionPreparation savedPreparation = preparations.saveAndFlush(lockedPreparation);
    inspections.saveAndFlush(inspection);
    return savedPreparation;
  }

  @Transactional
  public InspectionPreparation returnPreparation(
      UUID reviewerId, UUID inspectionId, UUID preparationId, ReadinessReturnCommand command) {
    UserAccess.ActiveUser reviewer = requireOrgAdmin(reviewerId);
    Inspection inspection = lockInspection(inspectionId, requireOrganization(reviewer));
    requireReviewableInspection(inspection);
    requireIndependentReviewer(reviewerId, inspection);
    lockCurrentSubmittedPreparation(inspection, preparationId);
    if (blank(command.reason())) {
      throw readinessFailure(
          "RETURN_REASON_REQUIRED", "A reason is required to return preparation.");
    }

    Instant decidedAt = Instant.now();
    requireReviewerCredential(
        command.reviewerCredentialId(), reviewer.organizationId(), reviewerId, decidedAt);
    compliance.evaluateReadiness(reviewerId, inspectionId);

    InspectionPreparation lockedPreparation =
        preparations
            .findWithLockByIdAndInspectionId(preparationId, inspectionId)
            .orElseThrow(InspectionReadinessService::preparationNotFound);
    lockedPreparation.returnForRevision(reviewerId, command.reason());
    return preparations.saveAndFlush(lockedPreparation);
  }

  private WorkforceCredentialSummary requireReviewerCredential(
      UUID credentialId, UUID organizationId, UUID reviewerId, Instant decidedAt) {
    WorkforceCredentialSummary credential =
        workforceCredentials
            .findByIdAndOrganizationIdAndUserId(credentialId, organizationId, reviewerId)
            .orElseThrow(
                () ->
                    readinessFailure(
                        "REVIEWER_CREDENTIAL_INVALID",
                        "Reviewer credential was not found in scope."));
    if (credential.status() != CredentialStatus.ACTIVE
        || credential.verifiedByUserId() == null
        || credential.verifiedAt() == null
        || credential.evidenceId() == null
        || credential.issuedAt() == null
        || credential.expiresAt() == null
        || credential.issuedAt().isAfter(decidedAt)
        || credential.expiresAt().isBefore(decidedAt)) {
      throw readinessFailure(
          "REVIEWER_CREDENTIAL_INVALID",
          "Reviewer credential is not active and internally verified.");
    }
    return credential;
  }

  private void requireCredentialValidAt(
      WorkforceCredentialSummary credential, Instant instant, String code) {
    if (credential.issuedAt() == null
        || credential.expiresAt() == null
        || instant.isBefore(credential.issuedAt())
        || instant.isAfter(credential.expiresAt())) {
      throw readinessFailure(code, "Credential does not cover the required instant.");
    }
  }

  private List<WorkforceCredentialSummary> resolveInspectorCredentials(
      List<UUID> ids, UUID organizationId, Inspection inspection) {
    List<WorkforceCredentialSummary> results = new ArrayList<>();
    for (UUID id : ids) {
      WorkforceCredentialSummary credential =
          workforceCredentials
              .findByIdAndOrganizationIdAndUserId(id, organizationId, inspection.getInspectorId())
              .orElseThrow(
                  () ->
                      readinessFailure(
                          "INSPECTOR_CREDENTIAL_INVALID",
                          "Selected Inspector credential was not found in scope."));
      if (credential.status() != CredentialStatus.ACTIVE
          || credential.verifiedByUserId() == null
          || credential.verifiedAt() == null
          || credential.evidenceId() == null) {
        throw readinessFailure(
            "INSPECTOR_CREDENTIAL_INVALID",
            "Selected Inspector credential lacks active verification evidence.");
      }
      requireCredentialValidAt(
          credential, inspection.getPlannedStartAt(), "INSPECTOR_CREDENTIAL_INVALID");
      results.add(credential);
    }
    return results;
  }

  private List<DroneDocumentSummary> resolveDroneDocuments(
      List<UUID> ids, UUID organizationId, Inspection inspection) {
    List<DroneDocumentSummary> documents =
        assetReadiness.findForDrone(organizationId, inspection.getDroneId(), ids);
    if (documents.size() != ids.stream().distinct().count()) {
      throw readinessFailure(
          "DRONE_DOCUMENT_INVALID",
          "One or more selected Drone documents were not found in scope.");
    }
    for (DroneDocumentSummary document : documents) {
      if (document.status() != DroneDocumentReadinessStatus.ACTIVE
          || document.reviewedByUserId() == null
          || document.reviewedAt() == null
          || document.validFrom() == null
          || document.validUntil() == null
          || inspection.getPlannedStartAt().isBefore(document.validFrom())
          || inspection.getPlannedStartAt().isAfter(document.validUntil())) {
        throw readinessFailure(
            "DRONE_DOCUMENT_INVALID",
            "Selected Drone document is unreviewed or outside its validity window.");
      }
    }
    return documents;
  }

  private AssetPairReadinessSummary requirePair(UUID organizationId, Inspection inspection) {
    if (inspection.getAssetPairAssignmentId() == null) {
      throw readinessFailure("PAIR_MISSING", "Inspection has no assigned pair.");
    }
    AssetPairReadinessSummary pair =
        assetReadiness
            .findPairForInspection(
                organizationId, inspection.getAssetPairAssignmentId(), inspection.getAssetId())
            .orElseThrow(
                () -> readinessFailure("PAIR_MISSING", "Assigned pair was not found in scope."));
    Instant start = inspection.getPlannedStartAt();
    if (pair.status() != AssetPairReadinessStatus.ACTIVE
        || pair.assignmentResponse() != AssignmentReadinessResponse.ACCEPTED
        || pair.respondedAt() == null
        || !pair.respondedAt().isBefore(start)) {
      throw readinessFailure(
          "PAIR_RESPONSE_LATE", "Pair must be active and accepted before planned start.");
    }
    if (pair.validFrom() == null
        || pair.validFrom().isAfter(start)
        || (pair.validUntil() != null && pair.validUntil().isBefore(start))) {
      throw readinessFailure("PAIR_INVALID", "Pair validity does not cover planned start.");
    }
    if (!pair.assetId().equals(inspection.getAssetId())
        || !pair.inspectorUserId().equals(inspection.getInspectorId())
        || !pair.droneId().equals(inspection.getDroneId())) {
      throw readinessFailure("PAIR_SCOPE_MISMATCH", "Pair does not match inspection assignment.");
    }
    return pair;
  }

  private void requireServiceableDrone(UUID organizationId, Inspection inspection) {
    if (inspection.getDroneId() == null) {
      throw readinessFailure("DRONE_MISSING", "Inspection has no assigned Drone.");
    }
    Drone drone =
        drones
            .findByIdAndOrganizationId(inspection.getDroneId(), organizationId)
            .orElseThrow(
                () -> readinessFailure("DRONE_MISSING", "Assigned Drone was not found in scope."));
    if (drone.getServiceability() != DroneServiceability.ACTIVE) {
      throw readinessFailure("DRONE_NOT_SERVICEABLE", "Assigned Drone is not active.");
    }
  }

  private Inspection lockInspection(UUID inspectionId, UUID organizationId) {
    Inspection inspection =
        inspections
            .findWithLockByIdAndOrganizationId(inspectionId, organizationId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND, "INSPECTION_NOT_FOUND", "Inspection was not found."));
    entityManager.refresh(inspection, LockModeType.PESSIMISTIC_WRITE);
    return inspection;
  }

  private void requireNoCurrentApprovalForAnotherPreparation(
      Inspection inspection, InspectionPreparation preparation) {
    boolean anotherReady =
        preparations.findByInspectionIdOrderByPreparationVersionDesc(inspection.getId()).stream()
            .filter(other -> other.getStatus() == InspectionPreparationStatus.READY)
            .anyMatch(
                other ->
                    !preparation.getId().equals(other.getId())
                        || preparation.getPreparationVersion() != other.getPreparationVersion());
    if (anotherReady) {
      throw readinessFailure(
          "READINESS_INVALIDATION_REQUIRED",
          "The prior approval must be invalidated before reviewing another preparation.");
    }
  }

  private InspectionPreparation lockCurrentSubmittedPreparation(
      Inspection inspection, UUID preparationId) {
    List<InspectionPreparation> versions =
        preparations.findByInspectionIdOrderByPreparationVersionDesc(inspection.getId());
    InspectionPreparation latest =
        versions.stream().findFirst().orElseThrow(InspectionReadinessService::preparationNotFound);
    if (!latest.getId().equals(preparationId)
        || !latest.belongsTo(inspection.getId())
        || latest.getStatus() != InspectionPreparationStatus.SUBMITTED) {
      throw readinessFailure(
          "PREPARATION_NOT_CURRENT",
          "Only the current submitted preparation assigned to this inspection can be reviewed.");
    }
    return preparations
        .findWithLockByIdAndInspectionId(preparationId, inspection.getId())
        .orElseThrow(InspectionReadinessService::preparationNotFound);
  }

  private void requireReviewableInspection(Inspection inspection) {
    if (inspection.getStatus() != InspectionStatus.PREPARING) {
      throw readinessFailure(
          "READINESS_NOT_ALLOWED", "Readiness review requires an inspection in PREPARING state.");
    }
  }

  private void requireIndependentReviewer(UUID reviewerId, Inspection inspection) {
    if (inspection.isInspectedBy(reviewerId)) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN,
          "REVIEWER_NOT_INDEPENDENT",
          "Reviewer cannot approve their own assignment.");
    }
  }

  private UserAccess.ActiveUser requireOrgAdmin(UUID reviewerId) {
    UserAccess.ActiveUser reviewer =
        userAccess
            .findActiveUser(reviewerId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.FORBIDDEN, "FORBIDDEN", "User is not active."));
    if (!reviewer.hasRole(Roles.ORG_MANAGER)) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN, "FORBIDDEN", "Role ORG_MANAGER is required.");
    }
    requireOrganization(reviewer);
    return reviewer;
  }

  private UUID requireOrganization(UserAccess.ActiveUser actor) {
    if (actor.organizationId() == null) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN, "FORBIDDEN", "User has no organization scope.");
    }
    return actor.organizationId();
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }

  private static BusinessException readinessFailure(String code, String message) {
    HttpStatus status =
        code.equals("REVIEWER_NOT_INDEPENDENT") ? HttpStatus.FORBIDDEN : HttpStatus.CONFLICT;
    return new BusinessException(status, code, message);
  }

  private static BusinessException preparationNotFound() {
    return new BusinessException(
        HttpStatus.NOT_FOUND, "PREPARATION_NOT_FOUND", "Preparation was not found.");
  }
}
