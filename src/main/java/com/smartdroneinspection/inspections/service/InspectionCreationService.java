package com.smartdroneinspection.inspections.service;

import com.smartdroneinspection.assets.domain.Asset;
import com.smartdroneinspection.assets.domain.AssetPairAssignment;
import com.smartdroneinspection.assets.domain.Drone;
import com.smartdroneinspection.assets.domain.FlightPermit;
import com.smartdroneinspection.assets.domain.enums.AssetPairAssignmentStatus;
import com.smartdroneinspection.assets.domain.enums.AssetStatus;
import com.smartdroneinspection.assets.domain.enums.DroneServiceability;
import com.smartdroneinspection.assets.repository.AssetPairAssignmentRepository;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.assets.repository.DroneRepository;
import com.smartdroneinspection.assets.repository.FlightPermitRepository;
import com.smartdroneinspection.inspections.api.dto.request.CreateInspectionRequest;
import com.smartdroneinspection.inspections.api.dto.response.InspectionResponse;
import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.shared.auth.Roles;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.users.UserAccess;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InspectionCreationService {

  private final InspectionRepository inspections;
  private final AssetRepository assets;
  private final AssetPairAssignmentRepository assetPairAssignments;
  private final DroneRepository drones;
  private final FlightPermitRepository permits;
  private final UserAccess userAccess;

  public InspectionCreationService(
      InspectionRepository inspections,
      AssetRepository assets,
      AssetPairAssignmentRepository assetPairAssignments,
      DroneRepository drones,
      FlightPermitRepository permits,
      UserAccess userAccess) {
    this.inspections = inspections;
    this.assets = assets;
    this.assetPairAssignments = assetPairAssignments;
    this.drones = drones;
    this.permits = permits;
    this.userAccess = userAccess;
  }

  @Transactional
  public InspectionResponse create(UUID actorId, CreateInspectionRequest request) {
    UserAccess.ActiveUser actor = requireOrgAdmin(actorId);
    UUID organizationId = actor.organizationId();

    Asset asset =
        assets
            .findByIdAndOrganizationId(request.assetId(), organizationId)
            .orElseThrow(
                () -> new BusinessException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Asset not found"));

    if (asset.getStatus() == AssetStatus.RETIRED) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "INVALID_STATE", "Cannot create an inspection for a retired asset.");
    }

    if (request.plannedStartAt() != null && request.plannedEndAt() != null) {
      if (!request.plannedEndAt().isAfter(request.plannedStartAt())) {
        throw new BusinessException(
            HttpStatus.BAD_REQUEST, "INVALID_DATES", "Planned end must be after planned start.");
      }
    }

    UUID inspectorId;
    UUID droneId;
    UUID assetPairAssignmentId;

    if (request.inspectorId() == null && request.droneId() == null) {
      AssetPairAssignment activePair =
          assetPairAssignments
              .findByOrganizationIdAndAssetIdAndStatus(
                  organizationId, asset.getId(), AssetPairAssignmentStatus.ACTIVE)
              .orElseThrow(
                  () ->
                      new BusinessException(
                          HttpStatus.BAD_REQUEST,
                          "PAIR_REQUIRED",
                          "Asset has no active default inspector and drone pair assigned."));
      inspectorId = activePair.getInspectorUserId();
      droneId = activePair.getDroneId();
      assetPairAssignmentId = activePair.getId();
    } else {
      if (request.inspectorId() == null || request.droneId() == null) {
        throw new BusinessException(
            HttpStatus.BAD_REQUEST,
            "PAIR_INCOMPLETE",
            "Both inspector and drone must be provided for reassignment.");
      }
      UserAccess.ActiveUser inspector =
          userAccess
              .findActiveUser(request.inspectorId())
              .filter(u -> organizationId.equals(u.organizationId()) && u.hasRole(Roles.INSPECTOR))
              .orElseThrow(
                  () ->
                      new BusinessException(
                          HttpStatus.BAD_REQUEST,
                          "INVALID_INSPECTOR",
                          "Assigned inspector must be active in the organization."));

      Drone drone =
          drones
              .findByIdAndOrganizationId(request.droneId(), organizationId)
              .filter(d -> d.getServiceability() != DroneServiceability.RETIRED)
              .orElseThrow(
                  () ->
                      new BusinessException(
                          HttpStatus.BAD_REQUEST,
                          "INVALID_DRONE",
                          "Assigned drone must be active in the organization."));

      Optional<AssetPairAssignment> activePair =
          assetPairAssignments.findByOrganizationIdAndAssetIdAndStatus(
              organizationId, asset.getId(), AssetPairAssignmentStatus.ACTIVE);
      inspectorId = inspector.id();
      droneId = drone.getId();
      assetPairAssignmentId = activePair.map(AssetPairAssignment::getId).orElse(null);
    }

    if (request.plannedStartAt() != null && request.plannedEndAt() != null) {
      if (inspections.hasScheduleConflict(
          organizationId, inspectorId, droneId, request.plannedStartAt(), request.plannedEndAt())) {
        throw new BusinessException(
            HttpStatus.CONFLICT,
            "SCHEDULE_CONFLICT",
            "The assigned inspector or drone has a schedule conflict with another active inspection in this window.");
      }
    }

    Inspection inspection =
        new Inspection(
            organizationId,
            asset.getId(),
            assetPairAssignmentId,
            inspectorId,
            droneId,
            request.objective(),
            request.scope(),
            request.componentScope(),
            request.acceptanceCriteria(),
            request.plannedStartAt(),
            request.plannedEndAt());

    inspection.dispatch();

    Inspection saved = inspections.saveAndFlush(inspection);
    return toResponse(saved, asset);
  }

  @Transactional(readOnly = true)
  public InspectionResponse get(UUID actorId, UUID inspectionId) {
    UserAccess.ActiveUser actor = requireActive(actorId);
    UUID organizationId = actor.organizationId();
    Inspection inspection =
        inspections
            .findByIdAndOrganizationId(inspectionId, organizationId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        HttpStatus.NOT_FOUND, "NOT_FOUND", "Inspection not found"));

    Asset asset =
        assets.findByIdAndOrganizationId(inspection.getAssetId(), organizationId).orElse(null);
    return toResponse(inspection, asset);
  }

  private InspectionResponse toResponse(Inspection inspection, Asset asset) {
    String assetName = asset == null ? null : asset.getName();
    String inspectorName =
        userAccess
            .findActiveUser(inspection.getInspectorId())
            .map(UserAccess.ActiveUser::fullName)
            .orElse(null);
    Drone drone =
        inspection.getDroneId() != null
            ? drones
                .findByIdAndOrganizationId(inspection.getDroneId(), inspection.getOrganizationId())
                .orElse(null)
            : null;

    FlightPermit permit =
        permits
            .findByOrganizationIdAndAssetIdOrderByCreatedAtDesc(
                inspection.getOrganizationId(), inspection.getAssetId())
            .stream()
            .findFirst()
            .orElse(null);

    return new InspectionResponse(
        inspection.getId(),
        inspection.getOrganizationId(),
        inspection.getAssetId(),
        assetName,
        asset == null ? null : asset.getLocationText(),
        inspection.getAssetPairAssignmentId(),
        inspection.getInspectorId(),
        inspectorName,
        inspection.getDroneId(),
        drone == null ? null : drone.getSerialNumber(),
        drone == null ? null : drone.getModel(),
        drone == null ? null : drone.getServiceability().name(),
        permit == null ? null : permit.getPermitReference(),
        permit == null ? null : permit.getIssuingAuthority(),
        permit == null ? null : permit.getValidFrom(),
        permit == null ? null : permit.getValidUntil(),
        permit == null ? null : permit.getReadinessStatus().name(),
        inspection.getObjective(),
        inspection.getScope(),
        inspection.getComponentScope(),
        inspection.getAcceptanceCriteria(),
        inspection.getPlannedStartAt(),
        inspection.getPlannedEndAt(),
        inspection.getStatus().name(),
        inspection.getCreatedAt(),
        inspection.getUpdatedAt());
  }

  private UserAccess.ActiveUser requireOrgAdmin(UUID actorId) {
    UserAccess.ActiveUser actor = requireActive(actorId);
    if (!actor.hasRole(Roles.ORG_MANAGER)) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN, "FORBIDDEN", "Only ORG_MANAGER can perform this action.");
    }
    if (actor.organizationId() == null) {
      throw new BusinessException(
          HttpStatus.FORBIDDEN, "FORBIDDEN", "Actor has no organization scope.");
    }
    return actor;
  }

  private UserAccess.ActiveUser requireActive(UUID actorId) {
    return userAccess
        .findActiveUser(actorId)
        .orElseThrow(
            () -> new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "User is not active."));
  }
}
