package com.smartdroneinspection.inspections.service;

import com.smartdroneinspection.assets.domain.Asset;
import com.smartdroneinspection.assets.domain.Drone;
import com.smartdroneinspection.assets.domain.enums.AssetStatus;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.assets.repository.DroneRepository;
import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.domain.enums.FindingStatus;
import com.smartdroneinspection.inspections.domain.enums.InspectionStatus;
import com.smartdroneinspection.inspections.events.InspectionAssignedEvent;
import com.smartdroneinspection.inspections.events.InspectionCancelledEvent;
import com.smartdroneinspection.inspections.repository.FindingRepository;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.inspections.spi.Mf2InspectionAssignment;
import com.smartdroneinspection.inspections.spi.Mf2InspectionAssignment.AssignmentAcceptance;
import com.smartdroneinspection.shared.auth.Roles;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.users.UserAccess;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class Mf2InspectionAssignmentAdapter implements Mf2InspectionAssignment {

  private final InspectionRepository inspections;
  private final AssetRepository assets;
  private final DroneRepository drones;
  private final UserAccess users;
  private final ApplicationEventPublisher events;
  private final Optional<FindingRepository> findings;
  private final Optional<ReinspectionVerificationService> reinspectionVerifications;

  public Mf2InspectionAssignmentAdapter(
      InspectionRepository inspections,
      AssetRepository assets,
      DroneRepository drones,
      UserAccess users,
      ApplicationEventPublisher events) {
    this(inspections, assets, drones, users, events, Optional.empty(), Optional.empty());
  }

  @Autowired
  public Mf2InspectionAssignmentAdapter(
      InspectionRepository inspections,
      AssetRepository assets,
      DroneRepository drones,
      UserAccess users,
      ApplicationEventPublisher events,
      Optional<FindingRepository> findings,
      Optional<ReinspectionVerificationService> reinspectionVerifications) {
    this.inspections = inspections;
    this.assets = assets;
    this.drones = drones;
    this.users = users;
    this.events = events;
    this.findings = findings != null ? findings : Optional.empty();
    this.reinspectionVerifications =
        reinspectionVerifications != null ? reinspectionVerifications : Optional.empty();
  }

  @Override
  @Transactional(readOnly = true)
  public boolean hasPlatformScheduleConflict(
      UUID inspectorId, UUID droneId, Instant plannedStartAt, Instant plannedEndAt) {
    return inspections.hasPlatformPartnerScheduleConflict(
        inspectorId, droneId, plannedStartAt, plannedEndAt);
  }

  @Override
  @Transactional(readOnly = true)
  public boolean hasPlatformScheduleConflictExcluding(
      UUID inspectorId,
      UUID droneId,
      Instant plannedStartAt,
      Instant plannedEndAt,
      UUID excludeInspectionId) {
    if (excludeInspectionId == null) {
      return hasPlatformScheduleConflict(inspectorId, droneId, plannedStartAt, plannedEndAt);
    }
    return inspections.hasPlatformPartnerScheduleConflictExcluding(
        inspectorId, droneId, plannedStartAt, plannedEndAt, excludeInspectionId);
  }

  @Override
  @Transactional(readOnly = true)
  public void validateReinspectionSource(
      UUID ownerCustomerId, UUID assetId, UUID sourceInspectionId) {
    if (sourceInspectionId == null) {
      return;
    }
    Inspection source =
        inspections
            .findById(sourceInspectionId)
            .filter(i -> ownerCustomerId != null && ownerCustomerId.equals(i.getOwnerCustomerId()))
            .orElseThrow(
                () ->
                    failure(
                        HttpStatus.NOT_FOUND,
                        "SOURCE_INSPECTION_NOT_FOUND",
                        "Source inspection not found for this organization"));
    if (assetId != null && !assetId.equals(source.getAssetId())) {
      throw failure(
          HttpStatus.BAD_REQUEST,
          "REINSPECTION_ASSET_MISMATCH",
          "Re-inspection request asset must match the source inspection asset");
    }
    if (source.getStatus() != InspectionStatus.COMPLETED) {
      throw failure(
          HttpStatus.CONFLICT,
          "SOURCE_INSPECTION_NOT_COMPLETED",
          "Complete and publish the source inspection before requesting a re-inspection");
    }
    if (inspections.existsOpenReinspectionForSource(sourceInspectionId)) {
      throw failure(
          HttpStatus.CONFLICT,
          "REINSPECTION_ALREADY_OPEN",
          "An open re-inspection already exists for this source inspection");
    }
    if (findings.isPresent()) {
      boolean hasConfirmed =
          findings.get().findByInspectionIdOrderByCreatedAtAsc(sourceInspectionId).stream()
              .anyMatch(f -> f.getStatus() == FindingStatus.CONFIRMED && !f.isDeleted());
      if (!hasConfirmed) {
        throw failure(
            HttpStatus.BAD_REQUEST,
            "NO_CONFIRMED_FINDINGS",
            "Source inspection must have at least one confirmed defect to schedule a re-inspection");
      }
    }
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<AssignmentAcceptance> findAssignmentState(UUID inspectionId) {
    if (inspectionId == null) {
      return Optional.empty();
    }
    return inspections
        .findById(inspectionId)
        .map(
            inspection ->
                new AssignmentAcceptance(
                    inspection.getAssignmentStatus() == null
                        ? null
                        : inspection.getAssignmentStatus().name(),
                    inspection.isReassignmentRequired(),
                    inspection.getDeclineReason(),
                    inspection.getAssignmentRequestedAt()));
  }

  @Override
  @Transactional
  public UUID assign(
      UUID ownerCustomerId,
      UUID assetId,
      UUID inspectorId,
      UUID droneId,
      Instant plannedStartAt,
      Instant plannedEndAt,
      UUID assignedByUserId) {
    return assign(
        ownerCustomerId,
        assetId,
        inspectorId,
        droneId,
        plannedStartAt,
        plannedEndAt,
        assignedByUserId,
        null);
  }

  @Override
  @Transactional
  public UUID assign(
      UUID ownerCustomerId,
      UUID assetId,
      UUID inspectorId,
      UUID droneId,
      Instant plannedStartAt,
      Instant plannedEndAt,
      UUID assignedByUserId,
      UUID sourceInspectionId) {
    return assign(
        ownerCustomerId,
        assetId,
        inspectorId,
        droneId,
        plannedStartAt,
        plannedEndAt,
        assignedByUserId,
        sourceInspectionId,
        null);
  }

  @Override
  @Transactional
  public UUID assign(
      UUID ownerCustomerId,
      UUID assetId,
      UUID inspectorId,
      UUID droneId,
      Instant plannedStartAt,
      Instant plannedEndAt,
      UUID assignedByUserId,
      UUID sourceInspectionId,
      List<String> inspectionItems) {
    users
        .findActiveUser(assignedByUserId)
        .filter(user -> user.hasRole(Roles.MANAGER))
        .orElseThrow(
            () ->
                failure(
                    HttpStatus.FORBIDDEN,
                    "FORBIDDEN",
                    "Only an active Manager can assign partner inspections"));
    if (ownerCustomerId == null || assetId == null || inspectorId == null || droneId == null) {
      throw failure(HttpStatus.BAD_REQUEST, "ASSIGNMENT_INVALID", "Assignment data is incomplete");
    }
    if (plannedStartAt == null || plannedEndAt == null || !plannedEndAt.isAfter(plannedStartAt)) {
      throw failure(
          HttpStatus.BAD_REQUEST, "INVALID_DATES", "Planned end must be after planned start");
    }

    UserAccess.ActiveUser inspector =
        users
            .findActiveUser(inspectorId)
            .filter(UserAccess.ActiveUser::isInspectorPartner)
            .filter(user -> user.hasValidPilotLicense(plannedStartAt))
            .orElseThrow(
                () ->
                    failure(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_INSPECTOR",
                        "Partner Inspector must be active and hold a valid pilot license"));
    Drone drone =
        drones
            .findWithLockById(droneId)
            .filter(value -> inspector.id().equals(value.getInspectorOwnerId()))
            .filter(value -> value.isEligibleForAssignment(plannedStartAt))
            .filter(Drone::hasKnownMtow)
            .orElseThrow(
                () ->
                    failure(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_DRONE",
                        "Partner drone must be approved for the selected Inspector and currently registered"));

    if (!inspector.isLicenseClassQualifiedForDrone(
        drone.hasKnownMtow(), drone.requiresClassBForSafety())) {
      throw failure(
          HttpStatus.BAD_REQUEST,
          "PILOT_LICENSE_CLASS_INSUFFICIENT",
          "Assigned drone has MTOW >= 2 kg (Category 3+ under Decree 288/2025/ND-CP) and requires a Class B pilot license");
    }

    Asset asset =
        assets
            .findWithLockByIdAndOwnerCustomerId(assetId, ownerCustomerId)
            .orElseThrow(
                () -> failure(HttpStatus.NOT_FOUND, "NOT_FOUND", "Customer asset not found"));
    if (asset.getStatus() == AssetStatus.RETIRED) {
      throw failure(HttpStatus.CONFLICT, "INVALID_STATE", "Cannot assign a retired asset");
    }
    if (sourceInspectionId != null) {
      validateReinspectionSource(ownerCustomerId, asset.getId(), sourceInspectionId);
    }
    if (inspections.hasPlatformPartnerScheduleConflict(
        inspector.id(), drone.getId(), plannedStartAt, plannedEndAt)) {
      throw failure(
          HttpStatus.CONFLICT,
          "SCHEDULE_CONFLICT",
          "The partner Inspector or drone conflicts with another active inspection");
    }

    UUID effectiveOwnerCustomerId =
        asset.getOwnerCustomerId() != null ? asset.getOwnerCustomerId() : ownerCustomerId;
    Inspection inspection =
        sourceInspectionId != null
            ? Inspection.createReinspection(
                effectiveOwnerCustomerId,
                asset.getId(),
                inspector.id(),
                drone.getId(),
                plannedStartAt,
                plannedEndAt,
                sourceInspectionId)
            : new Inspection(
                effectiveOwnerCustomerId,
                asset.getId(),
                inspector.id(),
                drone.getId(),
                plannedStartAt,
                plannedEndAt);
    inspection.markPartnerAssignment();
    inspection.markPendingAcceptance();
    inspection.markCustomerOwned(effectiveOwnerCustomerId);
    List<String> resolvedItems =
        inspectionItems != null && !inspectionItems.isEmpty()
            ? inspectionItems
            : asset.getInspectionItems();
    inspection.setInspectionItems(resolvedItems);
    Inspection saved = inspections.saveAndFlush(inspection);
    if (sourceInspectionId != null) {
      reinspectionVerifications.ifPresent(
          svc -> svc.initializeVerificationsForReinspection(saved.getId(), sourceInspectionId));
    }
    events.publishEvent(
        new InspectionAssignedEvent(
            saved.getId(),
            saved.getOwnerCustomerId(),
            saved.getInspectorId(),
            saved.getPlannedStartAt()));
    return saved.getId();
  }

  @Override
  @Transactional
  public void cancelInspection(UUID actorId, UUID inspectionId, String reason) {
    if (inspectionId == null) {
      return;
    }
    Inspection inspection =
        inspections
            .findWithLockById(inspectionId)
            .orElseThrow(() -> failure(HttpStatus.NOT_FOUND, "NOT_FOUND", "Inspection not found"));
    if (inspection.getStatus() == InspectionStatus.CANCELLED) {
      return;
    }
    if (inspection.getStatus() != InspectionStatus.SCHEDULED) {
      throw failure(
          HttpStatus.CONFLICT,
          "INVALID_STATE",
          "Cannot cancel an inspection whose status is " + inspection.getStatus());
    }
    inspection.cancel(reason);
    Inspection saved = inspections.saveAndFlush(inspection);
    events.publishEvent(
        new InspectionCancelledEvent(
            saved.getId(),
            saved.getOwnerCustomerId(),
            saved.getInspectorId(),
            reason,
            Instant.now()));
  }

  private static BusinessException failure(HttpStatus status, String code, String detail) {
    return new BusinessException(status, code, detail);
  }
}
