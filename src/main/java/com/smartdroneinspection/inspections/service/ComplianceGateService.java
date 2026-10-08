package com.smartdroneinspection.inspections.service;

import com.smartdroneinspection.assets.domain.Drone;
import com.smartdroneinspection.assets.domain.FlightPermit;
import com.smartdroneinspection.assets.domain.enums.FlightPermitStatus;
import com.smartdroneinspection.assets.repository.DroneRepository;
import com.smartdroneinspection.assets.repository.FlightPermitRepository;
import com.smartdroneinspection.inspections.api.dto.request.LinkPermitReferencesRequest;
import com.smartdroneinspection.inspections.api.dto.response.ComplianceGateResponse;
import com.smartdroneinspection.inspections.api.dto.response.InspectionPreparationResponse;
import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.domain.InspectionPreparation;
import com.smartdroneinspection.inspections.domain.enums.InspectionPreparationStatus;
import com.smartdroneinspection.inspections.repository.InspectionPreparationRepository;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.shared.auth.Roles;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.users.UserAccess;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * MF2-04 and MF2-05: an administrator links the permits the organization actually holds, and the
 * platform reports what stands in the way of a readiness decision.
 *
 * <p>This gate never clears a mission. It reads permit records that an authority issued or refused
 * and reports discrepancies, because SRS 3.4 is explicit that missing authorization cannot be
 * waived by an internal approval and that ambiguous authority or geographic conditions need human
 * verification rather than inferred clearance. An empty blocker list means nothing was found, not
 * that flight is lawful.
 *
 * <p>Every check is scoped to the organization, and permits are only ever read through the asset
 * they cover, so a permit belonging to another tenant or another asset cannot appear in a result.
 */
@Service
public class ComplianceGateService {

  /** Statuses that mean the authority has not given permission for this mission. */
  private static final List<FlightPermitStatus> ISSUED_STATUSES =
      List.of(FlightPermitStatus.ACTIVE, FlightPermitStatus.EXPIRED);

  private final InspectionPreparationRepository preparations;
  private final InspectionRepository inspections;
  private final FlightPermitRepository permits;
  private final DroneRepository drones;
  private final UserAccess userAccess;

  public ComplianceGateService(
      InspectionPreparationRepository preparations,
      InspectionRepository inspections,
      FlightPermitRepository permits,
      DroneRepository drones,
      UserAccess userAccess) {
    this.preparations = preparations;
    this.inspections = inspections;
    this.permits = permits;
    this.drones = drones;
    this.userAccess = userAccess;
  }

  /**
   * Links issued permits to an inspection's current preparation (MF2-04).
   *
   * <p>Each id is resolved against the caller's organization rather than stored as given, so a
   * caller cannot attach another tenant's permit to make a blocker disappear.
   */
  @Transactional
  public InspectionPreparationResponse linkPermitReferences(
      UUID actorId, UUID inspectionId, LinkPermitReferencesRequest request) {
    UUID organizationId = requireComplianceOfficer(actorId);
    Inspection inspection = requireScopedInspection(inspectionId, organizationId);

    for (UUID permitId : request.permitIds()) {
      permits
          .findByIdAndOrganizationId(permitId, organizationId)
          .orElseThrow(
              () ->
                  new BusinessException(
                      HttpStatus.FORBIDDEN,
                      "PERMIT_SCOPE_DENIED",
                      "One of the linked permits belongs to another organization."));
    }

    InspectionPreparation preparation =
        currentPreparation(inspection.getId())
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.CONFLICT,
                        "PREPARATION_MISSING",
                        "Prepare the mission before linking compliance documents."));

    try {
      preparation.recordPermitDocumentReferences(request.permitIds().toString());
    } catch (IllegalStateException exception) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "PREPARATION_NOT_EDITABLE", exception.getMessage());
    }

    preparations.saveAndFlush(preparation);
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

  /**
   * Reports the blockers standing between an inspection and a readiness decision (MF2-05).
   *
   * <p>Returns findings rather than throwing. MF2-07's reviewer needs to see every blocker at once,
   * so a refusal here would hide the rest of the list behind the first one.
   */
  @Transactional(readOnly = true)
  public ComplianceGateResponse evaluate(UUID actorId, UUID inspectionId) {
    UUID organizationId = requireComplianceOfficer(actorId);
    Inspection inspection = requireScopedInspection(inspectionId, organizationId);

    List<ComplianceGateResponse.Blocker> blockers = new ArrayList<>();
    Instant plannedStart = inspection.getPlannedStartAt();

    if (plannedStart == null) {
      blockers.add(
          new ComplianceGateResponse.Blocker(
              "PLANNED_SESSION_MISSING",
              "The inspection has no planned field-session time to check a permit against."));
    }

    List<FlightPermit> applicable =
        permits.findByOrganizationIdAndAssetIdOrderByCreatedAtDesc(
            organizationId, inspection.getAssetId());

    if (applicable.isEmpty()) {
      blockers.add(
          new ComplianceGateResponse.Blocker(
              "PERMIT_MISSING",
              "No permit is recorded for this asset. Link the authorization the organization holds."));
    }

    List<UUID> linked = new ArrayList<>();
    boolean requiresHumanVerification = false;

    for (FlightPermit permit : applicable) {
      ComplianceGateResponse.Blocker blocker = checkPermit(permit, plannedStart);
      if (blocker != null) {
        blockers.add(blocker);
        continue;
      }
      if (permit.getStatus() == FlightPermitStatus.NOT_APPLICABLE) {
        requiresHumanVerification = true;
      }
      linked.add(permit.getId());
    }

    addDroneBlocker(inspection, organizationId, blockers);

    return new ComplianceGateResponse(
        inspection.getId(),
        plannedStart,
        List.copyOf(blockers),
        List.copyOf(linked),
        requiresHumanVerification);
  }

  /**
   * One permit's verdict, or {@code null} when it supports the mission.
   *
   * <p>An exemption counts only when it records its legal basis. SRS 3.4 allows an exemption "if
   * genuinely applicable" and requires the legal basis and a supporting review to be recorded, so
   * an exemption with a blank basis is treated as no permit at all rather than as clearance.
   */
  private ComplianceGateResponse.Blocker checkPermit(FlightPermit permit, Instant plannedStart) {
    if (permit.getStatus() == FlightPermitStatus.NOT_APPLICABLE) {
      if (permit.getReviewReason() == null || permit.getReviewReason().isBlank()) {
        return new ComplianceGateResponse.Blocker(
            "EXEMPTION_BASIS_MISSING",
            "Permit "
                + permit.getId()
                + " is marked not applicable without recording the legal basis for the exemption.");
      }
      return null;
    }

    if (!ISSUED_STATUSES.contains(permit.getStatus())) {
      return new ComplianceGateResponse.Blocker(
          "PERMIT_NOT_ISSUED",
          "Permit "
              + permit.getId()
              + " is "
              + permit.getStatus()
              + ". An authority must issue it; an internal approval cannot substitute.");
    }

    if (plannedStart == null) {
      return null;
    }

    if (!permit.authorizesAt(plannedStart)) {
      return new ComplianceGateResponse.Blocker(
          "PERMIT_OUTSIDE_VALIDITY",
          "Permit "
              + permit.getId()
              + " does not cover the planned session time and must be re-checked or re-issued.");
    }

    return null;
  }

  private void addDroneBlocker(
      Inspection inspection, UUID organizationId, List<ComplianceGateResponse.Blocker> blockers) {
    if (inspection.getDroneId() == null) {
      return;
    }
    Drone drone =
        drones.findByIdAndOrganizationId(inspection.getDroneId(), organizationId).orElse(null);

    if (drone != null && !drone.isEligibleForAssignment()) {
      blockers.add(
          new ComplianceGateResponse.Blocker(
              "DRONE_NOT_SERVICEABLE",
              "The assigned drone is "
                  + drone.getServiceability()
                  + " and cannot support this mission."));
    }
  }

  /**
   * The preparation an administrator may still attach compliance documents to.
   *
   * <p>DRAFT or RETURNED only. Attaching permits to an already submitted or approved version would
   * change the compliance basis after a reviewer had judged it, which is what MF2-08 treats as an
   * invalidation rather than an edit.
   */
  private java.util.Optional<InspectionPreparation> currentPreparation(UUID inspectionId) {
    return preparations
        .findFirstByInspectionIdOrderByPreparationVersionDesc(inspectionId)
        .filter(
            preparation ->
                preparation.getStatus() == InspectionPreparationStatus.DRAFT
                    || preparation.getStatus() == InspectionPreparationStatus.RETURNED);
  }

  private Inspection requireScopedInspection(UUID inspectionId, UUID organizationId) {
    return inspections
        .findByIdAndOrganizationId(inspectionId, organizationId)
        .orElseThrow(
            () ->
                new BusinessException(
                    HttpStatus.NOT_FOUND, "INSPECTION_NOT_FOUND", "Inspection was not found."));
  }

  /**
   * Only an organization administrator runs this gate.
   *
   * <p>An inspector prepares and answers for a mission but does not certify its own compliance
   * basis, so MF2-05 and MF2-07 are separated from MF2-03 and MF2-06 by role as well as by step.
   */
  private UUID requireComplianceOfficer(UUID actorId) {
    UserAccess.ActiveUser actor =
        userAccess
            .findActiveUser(actorId)
            .orElseThrow(
                () ->
                    new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "User is not active"));

    if (!actor.hasRole(Roles.ORG_ADMIN)) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN, "FORBIDDEN", "Role " + Roles.ORG_ADMIN + " is required");
    }
    if (actor.organizationId() == null) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN, "FORBIDDEN", "User has no organization scope");
    }
    return actor.organizationId();
  }
}
