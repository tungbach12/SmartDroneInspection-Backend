package com.smartdroneinspection.contracts.service;

import com.smartdroneinspection.assets.domain.Drone;
import com.smartdroneinspection.assets.domain.enums.DroneServiceability;
import com.smartdroneinspection.assets.repository.DroneRepository;
import com.smartdroneinspection.contracts.domain.PartnerDrone;
import com.smartdroneinspection.contracts.domain.PartnerInspector;
import com.smartdroneinspection.contracts.repository.ContractPartnerDroneRepository;
import com.smartdroneinspection.contracts.repository.ContractPartnerInspectorRepository;
import com.smartdroneinspection.shared.auth.Roles;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.users.UserAccess;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PartnerRegistryService {

  private final ContractPartnerInspectorRepository inspectors;
  private final ContractPartnerDroneRepository partnerDrones;
  private final DroneRepository drones;
  private final UserAccess users;

  public PartnerRegistryService(
      ContractPartnerInspectorRepository inspectors,
      ContractPartnerDroneRepository partnerDrones,
      DroneRepository drones,
      UserAccess users) {
    this.inspectors = inspectors;
    this.partnerDrones = partnerDrones;
    this.drones = drones;
    this.users = users;
  }

  @Transactional
  public PartnerInspector registerInspector(UUID managerId, UUID inspectorUserId) {
    requireManager(managerId);
    UserAccess.ActiveUser inspector = requireActiveInspector(inspectorUserId);
    if (!inspector.hasValidPilotLicense(Instant.now())) {
      throw failure(
          HttpStatus.BAD_REQUEST,
          "PARTNER_LICENSE_REQUIRED",
          "Inspector requires a valid pilot license");
    }
    PartnerInspector current = inspectors.findWithLockByInspectorId(inspectorUserId).orElse(null);
    if (current != null) {
      current.changeStatus(true);
      return inspectors.saveAndFlush(current);
    }
    return inspectors.saveAndFlush(
        new PartnerInspector(inspector.id(), inspector.organizationId(), managerId));
  }

  @Transactional
  public PartnerDrone registerDrone(UUID managerId, UUID droneId) {
    requireManager(managerId);
    Drone drone = drones.findWithLockById(droneId).orElseThrow(this::droneNotFound);
    if (drone.getOwnershipType()
            != com.smartdroneinspection.assets.domain.enums.DroneOwnershipType.OWNED
        || drone.getServiceability() != DroneServiceability.ACTIVE
        || drone.getRegistrationNumber() == null
        || drone.getRegistrationNumber().isBlank()
        || drone.getRegistrationFileKey() == null
        || drone.getRegistrationFileKey().isBlank()
        || (drone.getRegistrationExpiresAt() != null
            && !drone.getRegistrationExpiresAt().isAfter(Instant.now()))) {
      throw failure(
          HttpStatus.BAD_REQUEST,
          "PARTNER_DRONE_INVALID",
          "Partner drone must be active, owned, and have current registration");
    }
    PartnerDrone current = partnerDrones.findWithLockByDroneId(droneId).orElse(null);
    if (current != null) {
      current.changeStatus(true);
      return partnerDrones.saveAndFlush(current);
    }
    return partnerDrones.saveAndFlush(
        new PartnerDrone(drone.getId(), drone.getOrganizationId(), managerId));
  }

  @Transactional
  public void setInspectorActive(UUID managerId, UUID inspectorUserId, boolean active) {
    requireManager(managerId);
    PartnerInspector partner =
        inspectors
            .findWithLockByInspectorId(inspectorUserId)
            .orElseThrow(
                () ->
                    failure(
                        HttpStatus.NOT_FOUND, "PARTNER_NOT_FOUND", "Partner Inspector not found"));
    partner.changeStatus(active);
    inspectors.saveAndFlush(partner);
  }

  @Transactional
  public void setDroneActive(UUID managerId, UUID droneId, boolean active) {
    requireManager(managerId);
    PartnerDrone partner =
        partnerDrones
            .findWithLockByDroneId(droneId)
            .orElseThrow(
                () ->
                    failure(HttpStatus.NOT_FOUND, "PARTNER_NOT_FOUND", "Partner drone not found"));
    partner.changeStatus(active);
    partnerDrones.saveAndFlush(partner);
  }

  @Transactional(readOnly = true)
  public List<InspectorOption> listInspectors(UUID managerId) {
    requireManager(managerId);
    return inspectors.findActive().stream()
        .map(
            partner ->
                users
                    .findActiveUser(partner.getInspectorUserId())
                    .map(
                        user ->
                            new InspectorOption(
                                user.id(),
                                user.fullName(),
                                partner.getOrganizationId(),
                                user.pilotLicenseExpiresAt()))
                    .orElse(null))
        .filter(java.util.Objects::nonNull)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<DroneOption> listDrones(UUID managerId) {
    requireManager(managerId);
    return partnerDrones.findActive().stream()
        .map(
            partner ->
                drones
                    .findByIdAndOrganizationId(partner.getDroneId(), partner.getOrganizationId())
                    .filter(Drone::isEligibleForAssignment)
                    .map(
                        drone ->
                            new DroneOption(
                                drone.getId(),
                                drone.getSerialNumber(),
                                drone.getModel(),
                                partner.getOrganizationId()))
                    .orElse(null))
        .filter(java.util.Objects::nonNull)
        .toList();
  }

  @Transactional
  public UserAccess.ActiveUser lockEligibleInspector(UUID inspectorUserId, Instant at) {
    PartnerInspector partner =
        inspectors
            .findWithLockByInspectorId(inspectorUserId)
            .filter(value -> "ACTIVE".equals(value.getStatus()))
            .orElseThrow(
                () ->
                    failure(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_PARTNER_INSPECTOR",
                        "Inspector is not registered as an active partner"));
    return users
        .findActiveUser(partner.getInspectorUserId())
        .filter(user -> user.hasRole(Roles.INSPECTOR) && user.hasValidPilotLicense(at))
        .orElseThrow(
            () ->
                failure(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_PARTNER_INSPECTOR",
                    "Partner Inspector is not active or has no valid pilot license"));
  }

  @Transactional
  public void lockEligibleDrone(UUID droneId) {
    PartnerDrone partner =
        partnerDrones
            .findWithLockByDroneId(droneId)
            .filter(value -> "ACTIVE".equals(value.getStatus()))
            .orElseThrow(
                () ->
                    failure(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_PARTNER_DRONE",
                        "Drone is not registered as an active partner resource"));
    drones
        .findWithLockById(droneId)
        .filter(value -> value.getOrganizationId().equals(partner.getOrganizationId()))
        .filter(Drone::isEligibleForAssignment)
        .orElseThrow(
            () ->
                failure(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_PARTNER_DRONE",
                    "Partner drone is not eligible"));
  }

  @Transactional(readOnly = true)
  public boolean isEligibleInspector(UUID inspectorUserId, Instant at) {
    return inspectors.findActiveByInspectorId(inspectorUserId).isPresent()
        && users
            .findActiveUser(inspectorUserId)
            .filter(user -> user.hasRole(Roles.INSPECTOR) && user.hasValidPilotLicense(at))
            .isPresent();
  }

  @Transactional(readOnly = true)
  public boolean isEligibleDrone(UUID droneId) {
    return partnerDrones.findActiveByDroneId(droneId).isPresent()
        && drones
            .findById(droneId)
            .filter(Drone::isEligibleForAssignment)
            .filter(
                drone ->
                    drone.getRegistrationExpiresAt() == null
                        || drone.getRegistrationExpiresAt().isAfter(Instant.now()))
            .isPresent();
  }

  @Transactional(readOnly = true)
  public UserAccess.ActiveUser requireEligibleInspector(UUID inspectorUserId) {
    PartnerInspector partner =
        inspectors
            .findActiveByInspectorId(inspectorUserId)
            .orElseThrow(
                () ->
                    failure(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_PARTNER_INSPECTOR",
                        "Inspector is not registered as an active partner"));
    return requireActiveInspector(partner.getInspectorUserId());
  }

  @Transactional(readOnly = true)
  public Drone requireEligibleDrone(UUID droneId) {
    PartnerDrone partner =
        partnerDrones
            .findActiveByDroneId(droneId)
            .orElseThrow(
                () ->
                    failure(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_PARTNER_DRONE",
                        "Drone is not registered as an active partner resource"));
    return drones
        .findByIdAndOrganizationId(partner.getDroneId(), partner.getOrganizationId())
        .filter(Drone::isEligibleForAssignment)
        .orElseThrow(
            () ->
                failure(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_PARTNER_DRONE",
                    "Partner drone is not eligible"));
  }

  private UserAccess.ActiveUser requireActiveInspector(UUID inspectorUserId) {
    return users
        .findActiveUser(inspectorUserId)
        .filter(user -> user.hasRole(Roles.INSPECTOR) && user.organizationId() != null)
        .orElseThrow(
            () ->
                failure(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_PARTNER_INSPECTOR",
                    "User is not an active organization Inspector"));
  }

  private void requireManager(UUID managerId) {
    UserAccess.ActiveUser actor = users.findActiveUser(managerId).orElseThrow(this::forbidden);
    if ((!actor.hasRole(Roles.MANAGER) && !actor.hasRole(Roles.ADMIN))
        || actor.organizationId() != null) {
      throw forbidden();
    }
  }

  private BusinessException forbidden() {
    return failure(
        HttpStatus.FORBIDDEN, "FORBIDDEN", "Only a Manager can manage partner resources");
  }

  private BusinessException droneNotFound() {
    return failure(HttpStatus.NOT_FOUND, "PARTNER_DRONE_NOT_FOUND", "Partner drone not found");
  }

  private static BusinessException failure(HttpStatus status, String code, String detail) {
    return new BusinessException(status, code, detail);
  }

  public record InspectorOption(
      UUID id, String fullName, UUID partnerOrganizationId, Instant licenseExpiresAt) {}

  public record DroneOption(
      UUID id, String serialNumber, String model, UUID partnerOrganizationId) {}
}
