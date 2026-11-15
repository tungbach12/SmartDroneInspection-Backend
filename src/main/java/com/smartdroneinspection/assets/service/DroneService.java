package com.smartdroneinspection.assets.service;

import com.smartdroneinspection.assets.api.dto.request.CreateDroneRequest;
import com.smartdroneinspection.assets.api.dto.request.UpdateDroneServiceabilityRequest;
import com.smartdroneinspection.assets.api.dto.response.DroneResponse;
import com.smartdroneinspection.assets.api.dto.response.DroneSelectionResponse;
import com.smartdroneinspection.assets.domain.Drone;
import com.smartdroneinspection.assets.domain.enums.DroneServiceability;
import com.smartdroneinspection.assets.repository.DroneRepository;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.users.UserAccess;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Organization-owned drone inventory.
 *
 * <p>Every operation derives the tenant from the authenticated actor, never from request data, so a
 * drone belonging to another organization is reported exactly like one that does not exist.
 */
@Service
public class DroneService {

  private static final String NOT_FOUND_MESSAGE = "Drone not found.";

  private final DroneRepository drones;
  private final UserAccess userAccess;

  public DroneService(DroneRepository drones, UserAccess userAccess) {
    this.drones = drones;
    this.userAccess = userAccess;
  }

  @Transactional
  public DroneResponse create(UUID actorId, CreateDroneRequest request) {
    UserAccess.ActiveUser actor = requireActive(actorId);
    requireOwnerCustomer(actor);
    requireJsonDocument(
        request.payloadMetadata(), "Payload metadata must be a valid JSON document.");
    requireCurrentRegistrationInput(
        request.registrationNumber(),
        request.registrationFileKey(),
        request.registrationExpiresAt());
    String serialNumber = request.serialNumber().trim();
    if (drones.existsBySerialNumberIgnoreCase(serialNumber)) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "DUPLICATE_SERIAL_NUMBER", "Serial number already exists");
    }
    Drone drone =
        new Drone(
            actor.ownerCustomerId(),
            serialNumber,
            request.model(),
            request.manufacturer(),
            request.payloadMetadata(),
            DroneServiceability.ACTIVE,
            request.notes(),
            request.registrationNumber(),
            request.registrationFileKey(),
            request.registrationExpiresAt());
    drone.markCustomerOwned(actorId);
    return DroneResponse.from(drones.saveAndFlush(drone));
  }

  @Transactional(readOnly = true)
  public List<DroneResponse> list(UUID actorId) {
    UserAccess.ActiveUser actor = requireActive(actorId);
    if (actor.hasRole("ADMIN") || actor.hasRole("MANAGER")) {
      return drones.findAll().stream().map(DroneResponse::from).toList();
    }
    UUID ownerCustomerId = requireOwnerCustomer(actor);
    return drones.findAllByOwnerCustomerId(ownerCustomerId).stream()
        .map(DroneResponse::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public DroneResponse get(UUID actorId, UUID droneId) {
    return DroneResponse.from(requireOwnedDrone(actorId, droneId));
  }

  @Transactional
  public DroneResponse updateServiceability(
      UUID actorId, UUID droneId, UpdateDroneServiceabilityRequest request) {
    Drone drone = requireOwnedDrone(actorId, droneId);
    if (drone.getServiceability() == DroneServiceability.RETIRED) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "INVALID_STATE", "Retired drones cannot change serviceability");
    }
    drone.changeServiceability(request.serviceability());
    return DroneResponse.from(drones.saveAndFlush(drone));
  }

  @Transactional
  public DroneResponse updateRegistration(
      UUID actorId,
      UUID droneId,
      com.smartdroneinspection.assets.api.dto.request.UpdateDroneRegistrationRequest request) {
    Drone drone = requireOwnedDrone(actorId, droneId);
    String registrationNumber =
        request.registrationNumber() != null
            ? request.registrationNumber()
            : drone.getRegistrationNumber();
    String registrationFileKey =
        request.registrationFileKey() != null
            ? request.registrationFileKey()
            : drone.getRegistrationFileKey();
    Instant registrationExpiresAt =
        request.registrationExpiresAt() != null
            ? request.registrationExpiresAt()
            : drone.getRegistrationExpiresAt();
    requireCurrentRegistrationInput(registrationNumber, registrationFileKey, registrationExpiresAt);
    drone.updateRegistration(registrationNumber, registrationFileKey, registrationExpiresAt);
    return DroneResponse.from(drones.saveAndFlush(drone));
  }

  /**
   * Lists the organization's serviceable airframes. Serviceability is the only filter applied: this
   * query makes no assertion about permits or flight readiness, which are owned by other flows.
   */
  @Transactional(readOnly = true)
  public List<DroneSelectionResponse> selection(UUID actorId) {
    UUID ownerCustomerId = requireOwnerCustomer(requireActive(actorId));
    Instant now = Instant.now();
    return drones
        .findAllByOwnerCustomerIdAndServiceability(ownerCustomerId, DroneServiceability.ACTIVE)
        .stream()
        .filter(drone -> drone.isEligibleForAssignment(now))
        .filter(Drone::hasKnownMtow)
        .map(DroneSelectionResponse::from)
        .toList();
  }

  private Drone requireOwnedDrone(UUID actorId, UUID droneId) {
    UUID ownerCustomerId = requireOwnerCustomer(requireActive(actorId));
    return drones
        .findByIdAndOwnerCustomerId(droneId, ownerCustomerId)
        .orElseThrow(
            () ->
                new BusinessException(HttpStatus.NOT_FOUND, "DRONE_NOT_FOUND", NOT_FOUND_MESSAGE));
  }

  /** Rejects malformed JSON before it reaches the PostgreSQL jsonb column as an opaque 500. */
  private void requireCurrentRegistrationInput(
      String registrationNumber, String registrationFileKey, Instant registrationExpiresAt) {
    if (registrationNumber == null
        || registrationNumber.isBlank()
        || registrationFileKey == null
        || registrationFileKey.isBlank()
        || registrationExpiresAt == null
        || !registrationExpiresAt.isAfter(Instant.now())) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST,
          "DRONE_REGISTRATION_INVALID",
          "Registration number, evidence and a future expiry date are required.");
    }
  }

  private void requireJsonDocument(String value, String message) {
    if (value == null) {
      return;
    }
    try {
      tools.jackson.databind.json.JsonMapper.shared().readTree(value);
    } catch (RuntimeException ex) {
      throw new IllegalArgumentException(message);
    }
  }

  private UUID requireOwnerCustomer(UserAccess.ActiveUser actor) {
    UUID ownerCustomerId = actor.ownerCustomerId();
    if (ownerCustomerId == null) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Actor has no customer ownership scope");
    }
    return ownerCustomerId;
  }

  private UserAccess.ActiveUser requireActive(UUID actorId) {
    return userAccess
        .findActiveUser(actorId)
        .orElseThrow(
            () -> new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "User is not active"));
  }
}
