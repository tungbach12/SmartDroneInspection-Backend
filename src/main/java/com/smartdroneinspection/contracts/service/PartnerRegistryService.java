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

  private static final String INVALID_PARTNER_INSPECTOR = "INVALID_PARTNER_INSPECTOR";
  private static final String INVALID_PARTNER_DRONE = "INVALID_PARTNER_DRONE";
  private static final String PARTNER_NOT_FOUND = "PARTNER_NOT_FOUND";

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
    UserAccess.ActiveUser inspector = requireActivePartnerInspector(inspectorUserId);
    PartnerInspector current = inspectors.findWithLockByInspectorId(inspectorUserId).orElse(null);
    if (current != null) {
      current.changeStatus(true);
      return inspectors.saveAndFlush(current);
    }
    return inspectors.saveAndFlush(new PartnerInspector(inspector.id(), managerId));
  }

  @Transactional(readOnly = true)
  public InspectorOption getInspector(UUID managerId, UUID inspectorUserId) {
    requireManager(managerId);
    PartnerInspector partner =
        inspectors
            .findById(inspectorUserId)
            .filter(value -> "ACTIVE".equals(value.getStatus()))
            .orElseThrow(
                () ->
                    failure(
                        HttpStatus.NOT_FOUND, PARTNER_NOT_FOUND, "Partner Inspector not found"));
    UserAccess.ActiveUser inspector = requireActivePartnerInspector(partner.getInspectorUserId());
    return new InspectorOption(
        inspector.id(), inspector.fullName(), inspector.pilotLicenseExpiresAt());
  }

  @Transactional
  public PartnerDrone registerDrone(UUID managerId, UUID droneId) {
    requireManager(managerId);
    Drone drone = drones.findWithLockById(droneId).orElseThrow(this::droneNotFound);
    if (drone.getInspectorOwnerId() == null
        || drone.getServiceability() != DroneServiceability.ACTIVE
        || (drone.getRegistrationExpiresAt() != null
            && !drone.getRegistrationExpiresAt().isAfter(Instant.now()))) {
      throw failure(
          HttpStatus.BAD_REQUEST,
          "PARTNER_DRONE_INVALID",
          "Drone must be approved for an active Inspector partner with current registration");
    }
    requireActivePartnerInspector(drone.getInspectorOwnerId());
    PartnerDrone current = partnerDrones.findWithLockByDroneId(droneId).orElse(null);
    if (current != null) {
      current.changeStatus(true);
      current.changeInspectorOwner(drone.getInspectorOwnerId());
      return partnerDrones.saveAndFlush(current);
    }
    return partnerDrones.saveAndFlush(
        new PartnerDrone(drone.getId(), drone.getInspectorOwnerId(), managerId));
  }

  @Transactional
  public void setInspectorActive(UUID managerId, UUID inspectorUserId, boolean active) {
    requireManager(managerId);
    if (active) {
      requireActivePartnerInspector(inspectorUserId);
    }
    PartnerInspector partner =
        inspectors
            .findWithLockByInspectorId(inspectorUserId)
            .orElseThrow(
                () ->
                    failure(
                        HttpStatus.NOT_FOUND, PARTNER_NOT_FOUND, "Partner Inspector not found"));
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
                () -> failure(HttpStatus.NOT_FOUND, PARTNER_NOT_FOUND, "Partner drone not found"));
    if (active) {
      requireActivePartnerInspector(partner.getInspectorOwnerId());
    }
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
                    .filter(UserAccess.ActiveUser::isInspectorPartner)
                    .filter(user -> user.hasValidPilotLicense(Instant.now()))
                    .map(
                        user ->
                            new InspectorOption(
                                user.id(), user.fullName(), user.pilotLicenseExpiresAt()))
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
                    .findById(partner.getDroneId())
                    .filter(
                        drone ->
                            partner.getInspectorOwnerId() != null
                                && partner
                                    .getInspectorOwnerId()
                                    .equals(drone.getInspectorOwnerId()))
                    .filter(Drone::isEligibleForAssignment)
                    .filter(
                        drone ->
                            drone.getRegistrationExpiresAt() == null
                                || drone.getRegistrationExpiresAt().isAfter(Instant.now()))
                    .map(
                        drone ->
                            new DroneOption(
                                drone.getId(),
                                drone.getInspectorOwnerId(),
                                drone.getSerialNumber(),
                                drone.getModel()))
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
                        INVALID_PARTNER_INSPECTOR,
                        "Inspector is not registered as an active partner"));
    return users
        .findActiveUser(partner.getInspectorUserId())
        .filter(UserAccess.ActiveUser::isInspectorPartner)
        .filter(user -> user.hasValidPilotLicense(at))
        .orElseThrow(
            () ->
                failure(
                    HttpStatus.BAD_REQUEST,
                    INVALID_PARTNER_INSPECTOR,
                    "Partner Inspector is not active or has no valid pilot license"));
  }

  @Transactional
  public void lockEligibleDrone(UUID droneId, UUID inspectorUserId) {
    PartnerDrone partner =
        partnerDrones
            .findWithLockByDroneId(droneId)
            .filter(value -> "ACTIVE".equals(value.getStatus()))
            .filter(value -> inspectorUserId.equals(value.getInspectorOwnerId()))
            .orElseThrow(
                () ->
                    failure(
                        HttpStatus.BAD_REQUEST,
                        INVALID_PARTNER_DRONE,
                        "Drone is not registered for this active partner Inspector"));
    drones
        .findWithLockById(droneId)
        .filter(value -> inspectorUserId.equals(value.getInspectorOwnerId()))
        .filter(Drone::isEligibleForAssignment)
        .filter(
            value ->
                value.getRegistrationExpiresAt() == null
                    || value.getRegistrationExpiresAt().isAfter(Instant.now()))
        .orElseThrow(
            () ->
                failure(
                    HttpStatus.BAD_REQUEST,
                    INVALID_PARTNER_DRONE,
                    "Partner drone is not eligible"));
  }

  @Transactional(readOnly = true)
  public boolean isEligibleInspector(UUID inspectorUserId, Instant at) {
    return inspectors.findActiveByInspectorId(inspectorUserId).isPresent()
        && users
            .findActiveUser(inspectorUserId)
            .filter(UserAccess.ActiveUser::isInspectorPartner)
            .filter(user -> user.hasValidPilotLicense(at))
            .isPresent();
  }

  @Transactional(readOnly = true)
  public boolean isEligibleDrone(UUID droneId, UUID inspectorUserId) {
    return partnerDrones
            .findActiveByDroneIdAndInspectorOwnerId(droneId, inspectorUserId)
            .isPresent()
        && drones
            .findById(droneId)
            .filter(drone -> inspectorUserId.equals(drone.getInspectorOwnerId()))
            .filter(Drone::isEligibleForAssignment)
            .filter(
                drone ->
                    drone.getRegistrationExpiresAt() == null
                        || drone.getRegistrationExpiresAt().isAfter(Instant.now()))
            .isPresent();
  }

  @Transactional(readOnly = true)
  public UserAccess.ActiveUser requireEligibleInspector(UUID inspectorUserId) {
    inspectors
        .findActiveByInspectorId(inspectorUserId)
        .orElseThrow(
            () ->
                failure(
                    HttpStatus.BAD_REQUEST,
                    INVALID_PARTNER_INSPECTOR,
                    "Inspector is not registered as an active partner"));
    return requireActivePartnerInspector(inspectorUserId);
  }

  @Transactional(readOnly = true)
  public Drone requireEligibleDrone(UUID droneId, UUID inspectorUserId) {
    PartnerDrone partner =
        partnerDrones
            .findActiveByDroneIdAndInspectorOwnerId(droneId, inspectorUserId)
            .orElseThrow(
                () ->
                    failure(
                        HttpStatus.BAD_REQUEST,
                        INVALID_PARTNER_DRONE,
                        "Drone is not registered for this active partner Inspector"));
    return drones
        .findById(droneId)
        .filter(drone -> inspectorUserId.equals(drone.getInspectorOwnerId()))
        .filter(Drone::isEligibleForAssignment)
        .filter(
            drone ->
                drone.getRegistrationExpiresAt() == null
                    || drone.getRegistrationExpiresAt().isAfter(Instant.now()))
        .orElseThrow(
            () ->
                failure(
                    HttpStatus.BAD_REQUEST,
                    INVALID_PARTNER_DRONE,
                    "Partner drone is not eligible"));
  }

  private UserAccess.ActiveUser requireActivePartnerInspector(UUID inspectorUserId) {
    return users
        .findActiveUser(inspectorUserId)
        .filter(UserAccess.ActiveUser::isInspectorPartner)
        .filter(user -> user.hasValidPilotLicense(Instant.now()))
        .orElseThrow(
            () ->
                failure(
                    HttpStatus.BAD_REQUEST,
                    INVALID_PARTNER_INSPECTOR,
                    "User is not an active Inspector partner with a valid license"));
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

  public record InspectorOption(UUID id, String fullName, Instant licenseExpiresAt) {}

  public record DroneOption(UUID id, UUID inspectorOwnerId, String serialNumber, String model) {}
}
