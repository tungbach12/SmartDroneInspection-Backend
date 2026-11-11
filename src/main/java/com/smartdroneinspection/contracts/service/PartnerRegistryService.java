package com.smartdroneinspection.contracts.service;

import com.smartdroneinspection.assets.domain.Drone;
import com.smartdroneinspection.assets.domain.enums.DroneServiceability;
import com.smartdroneinspection.assets.repository.DroneRepository;
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

  private final DroneRepository drones;
  private final UserAccess users;

  public PartnerRegistryService(DroneRepository drones, UserAccess users) {
    this.drones = drones;
    this.users = users;
  }

  @Transactional
  public InspectorOption registerInspector(UUID managerId, UUID inspectorUserId) {
    requireManager(managerId);
    UserAccess.ActiveUser inspector = requireActivePartnerInspector(inspectorUserId);
    return new InspectorOption(
        inspector.id(), inspector.fullName(), inspector.pilotLicenseExpiresAt());
  }

  @Transactional(readOnly = true)
  public InspectorOption getInspector(UUID managerId, UUID inspectorUserId) {
    requireManager(managerId);
    UserAccess.ActiveUser inspector =
        users
            .findActiveUser(inspectorUserId)
            .filter(UserAccess.ActiveUser::isInspectorPartner)
            .filter(user -> user.hasValidPilotLicense(Instant.now()))
            .orElseThrow(
                () ->
                    failure(
                        HttpStatus.NOT_FOUND, PARTNER_NOT_FOUND, "Partner Inspector not found"));
    return new InspectorOption(
        inspector.id(), inspector.fullName(), inspector.pilotLicenseExpiresAt());
  }

  @Transactional
  public DroneOption registerDrone(UUID managerId, UUID droneId) {
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
    return new DroneOption(
        drone.getId(), drone.getInspectorOwnerId(), drone.getSerialNumber(), drone.getModel());
  }

  @Transactional
  public void setInspectorActive(UUID managerId, UUID inspectorUserId, boolean active) {
    requireManager(managerId);
    if (active) {
      requireActivePartnerInspector(inspectorUserId);
    }
  }

  @Transactional
  public void setDroneActive(UUID managerId, UUID droneId, boolean active) {
    requireManager(managerId);
    Drone drone =
        drones
            .findWithLockById(droneId)
            .filter(value -> value.getInspectorOwnerId() != null)
            .orElseThrow(
                () -> failure(HttpStatus.NOT_FOUND, PARTNER_NOT_FOUND, "Partner drone not found"));
    if (active) {
      requireActivePartnerInspector(drone.getInspectorOwnerId());
    }
  }

  @Transactional(readOnly = true)
  public List<InspectorOption> listInspectors(UUID managerId) {
    requireManager(managerId);
    Instant now = Instant.now();
    return users.listActiveInspectorPartners().stream()
        .filter(UserAccess.ActiveUser::isInspectorPartner)
        .filter(user -> user.hasValidPilotLicense(now))
        .map(user -> new InspectorOption(user.id(), user.fullName(), user.pilotLicenseExpiresAt()))
        .toList();
  }

  @Transactional(readOnly = true)
  public List<DroneOption> listDrones(UUID managerId) {
    requireManager(managerId);
    Instant now = Instant.now();
    return drones.findActivePartnerDrones().stream()
        .filter(drone -> drone.getInspectorOwnerId() != null)
        .filter(Drone::isEligibleForAssignment)
        .filter(
            drone ->
                drone.getRegistrationExpiresAt() == null
                    || drone.getRegistrationExpiresAt().isAfter(now))
        .filter(drone -> isEligibleInspector(drone.getInspectorOwnerId(), now))
        .map(
            drone ->
                new DroneOption(
                    drone.getId(),
                    drone.getInspectorOwnerId(),
                    drone.getSerialNumber(),
                    drone.getModel()))
        .toList();
  }

  @Transactional
  public UserAccess.ActiveUser lockEligibleInspector(UUID inspectorUserId, Instant at) {
    return users
        .findActiveUser(inspectorUserId)
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
    lockEligibleDrone(droneId, inspectorUserId, Instant.now());
  }

  @Transactional
  public void lockEligibleDrone(UUID droneId, UUID inspectorUserId, Instant at) {
    Instant checkTime = at != null ? at : Instant.now();
    UserAccess.ActiveUser inspector = lockEligibleInspector(inspectorUserId, checkTime);
    Drone drone =
        drones
            .findWithLockById(droneId)
            .filter(value -> inspectorUserId.equals(value.getInspectorOwnerId()))
            .filter(Drone::isEligibleForAssignment)
            .filter(
                value ->
                    value.getRegistrationExpiresAt() == null
                        || value.getRegistrationExpiresAt().isAfter(checkTime))
            .orElseThrow(
                () ->
                    failure(
                        HttpStatus.BAD_REQUEST,
                        INVALID_PARTNER_DRONE,
                        "Partner drone is not eligible or registration expires before the scheduled mission"));
    if (!inspector.isLicenseClassQualifiedFor(drone.requiresClassBLicense())) {
      throw failure(
          HttpStatus.BAD_REQUEST,
          "PILOT_LICENSE_CLASS_INSUFFICIENT",
          "Drone has MTOW >= 2 kg (Category 3+ under Decree 288/2025/ND-CP) and requires a Class B pilot license");
    }
  }

  @Transactional(readOnly = true)
  public boolean isEligibleInspector(UUID inspectorUserId, Instant at) {
    return users
        .findActiveUser(inspectorUserId)
        .filter(UserAccess.ActiveUser::isInspectorPartner)
        .filter(user -> user.hasValidPilotLicense(at))
        .isPresent();
  }

  @Transactional(readOnly = true)
  public boolean isEligibleInspectorForAllDates(
      UUID inspectorUserId, List<Instant> scheduledStarts) {
    if (scheduledStarts == null || scheduledStarts.isEmpty()) {
      return false;
    }
    UserAccess.ActiveUser inspector =
        users
            .findActiveUser(inspectorUserId)
            .filter(UserAccess.ActiveUser::isInspectorPartner)
            .orElse(null);
    return inspector != null && scheduledStarts.stream().allMatch(inspector::hasValidPilotLicense);
  }

  @Transactional(readOnly = true)
  public boolean isEligibleDrone(UUID droneId, UUID inspectorUserId) {
    return isEligibleDrone(droneId, inspectorUserId, Instant.now());
  }

  @Transactional(readOnly = true)
  public boolean isEligibleDrone(UUID droneId, UUID inspectorUserId, Instant at) {
    Instant checkTime = at != null ? at : Instant.now();
    UserAccess.ActiveUser inspector =
        users
            .findActiveUser(inspectorUserId)
            .filter(UserAccess.ActiveUser::isInspectorPartner)
            .filter(user -> user.hasValidPilotLicense(checkTime))
            .orElse(null);
    if (inspector == null) {
      return false;
    }
    return drones
        .findById(droneId)
        .filter(drone -> inspectorUserId.equals(drone.getInspectorOwnerId()))
        .filter(Drone::isEligibleForAssignment)
        .filter(
            drone ->
                drone.getRegistrationExpiresAt() == null
                    || drone.getRegistrationExpiresAt().isAfter(checkTime))
        .filter(drone -> inspector.isLicenseClassQualifiedFor(drone.requiresClassBLicense()))
        .isPresent();
  }

  @Transactional(readOnly = true)
  public boolean isEligibleDroneForAllDates(
      UUID droneId, UUID inspectorUserId, List<Instant> scheduledStarts) {
    if (scheduledStarts == null || scheduledStarts.isEmpty()) {
      return false;
    }
    Drone drone =
        drones
            .findById(droneId)
            .filter(value -> inspectorUserId.equals(value.getInspectorOwnerId()))
            .filter(Drone::isEligibleForAssignment)
            .orElse(null);
    UserAccess.ActiveUser inspector =
        users
            .findActiveUser(inspectorUserId)
            .filter(UserAccess.ActiveUser::isInspectorPartner)
            .orElse(null);
    if (drone == null || inspector == null) {
      return false;
    }
    boolean droneClassQualified =
        inspector.isLicenseClassQualifiedFor(drone.requiresClassBLicense());
    if (!droneClassQualified) {
      return false;
    }
    return scheduledStarts.stream()
        .allMatch(
            start ->
                inspector.hasValidPilotLicense(start)
                    && (drone.getRegistrationExpiresAt() == null
                        || drone.getRegistrationExpiresAt().isAfter(start)));
  }

  @Transactional(readOnly = true)
  public UserAccess.ActiveUser requireEligibleInspector(UUID inspectorUserId) {
    return requireActivePartnerInspector(inspectorUserId);
  }

  @Transactional(readOnly = true)
  public Drone requireEligibleDrone(UUID droneId, UUID inspectorUserId) {
    requireActivePartnerInspector(inspectorUserId);
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
