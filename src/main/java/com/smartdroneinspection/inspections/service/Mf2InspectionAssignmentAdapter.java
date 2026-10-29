package com.smartdroneinspection.inspections.service;

import com.smartdroneinspection.assets.domain.Asset;
import com.smartdroneinspection.assets.domain.Drone;
import com.smartdroneinspection.assets.domain.enums.AssetStatus;
import com.smartdroneinspection.assets.domain.enums.DroneServiceability;
import com.smartdroneinspection.assets.repository.AssetRepository;
import com.smartdroneinspection.assets.repository.DroneRepository;
import com.smartdroneinspection.inspections.domain.Inspection;
import com.smartdroneinspection.inspections.events.InspectionAssignedEvent;
import com.smartdroneinspection.inspections.repository.InspectionRepository;
import com.smartdroneinspection.inspections.spi.Mf2InspectionAssignment;
import com.smartdroneinspection.shared.auth.Roles;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.users.UserAccess;
import java.time.Instant;
import java.util.UUID;
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

  public Mf2InspectionAssignmentAdapter(
      InspectionRepository inspections,
      AssetRepository assets,
      DroneRepository drones,
      UserAccess users,
      ApplicationEventPublisher events) {
    this.inspections = inspections;
    this.assets = assets;
    this.drones = drones;
    this.users = users;
    this.events = events;
  }

  @Override
  @Transactional
  public UUID assign(
      UUID customerOrganizationId,
      UUID assetId,
      UUID inspectorId,
      UUID droneId,
      Instant plannedStartAt,
      Instant plannedEndAt,
      UUID assignedByUserId) {
    users
        .findActiveUser(assignedByUserId)
        .filter(
            user ->
                (user.hasRole(Roles.MANAGER) || user.hasRole(Roles.ADMIN))
                    && user.organizationId() == null)
        .orElseThrow(
            () ->
                failure(
                    HttpStatus.FORBIDDEN,
                    "FORBIDDEN",
                    "Only an active Manager can assign partner inspections"));
    if (customerOrganizationId == null
        || assetId == null
        || inspectorId == null
        || droneId == null) {
      throw failure(HttpStatus.BAD_REQUEST, "ASSIGNMENT_INVALID", "Assignment data is incomplete");
    }
    if (plannedStartAt == null || plannedEndAt == null || !plannedEndAt.isAfter(plannedStartAt)) {
      throw failure(
          HttpStatus.BAD_REQUEST, "INVALID_DATES", "Planned end must be after planned start");
    }

    Asset asset =
        assets
            .findWithLockByIdAndOrganizationId(assetId, customerOrganizationId)
            .orElseThrow(
                () -> failure(HttpStatus.NOT_FOUND, "NOT_FOUND", "Customer asset not found"));
    if (asset.getStatus() == AssetStatus.RETIRED) {
      throw failure(HttpStatus.CONFLICT, "INVALID_STATE", "Cannot assign a retired asset");
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
            .filter(value -> value.getServiceability() == DroneServiceability.ACTIVE)
            .filter(
                value ->
                    value.getRegistrationExpiresAt() == null
                        || value.getRegistrationExpiresAt().isAfter(plannedStartAt))
            .orElseThrow(
                () ->
                    failure(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_DRONE",
                        "Partner drone must be approved for the selected Inspector and currently registered"));

    if (inspections.hasPlatformPartnerScheduleConflict(
        inspector.id(), drone.getId(), plannedStartAt, plannedEndAt)) {
      throw failure(
          HttpStatus.CONFLICT,
          "SCHEDULE_CONFLICT",
          "The partner Inspector or drone conflicts with another active inspection");
    }

    Inspection inspection =
        new Inspection(
            customerOrganizationId,
            asset.getId(),
            inspector.id(),
            drone.getId(),
            plannedStartAt,
            plannedEndAt);
    inspection.markPartnerAssignment();
    Inspection saved = inspections.saveAndFlush(inspection);
    events.publishEvent(
        new InspectionAssignedEvent(
            saved.getId(),
            saved.getOrganizationId(),
            saved.getInspectorId(),
            saved.getPlannedStartAt()));
    return saved.getId();
  }

  private static BusinessException failure(HttpStatus status, String code, String detail) {
    return new BusinessException(status, code, detail);
  }
}
