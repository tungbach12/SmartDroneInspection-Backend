package com.smartdroneinspection.assets.service;

import com.smartdroneinspection.assets.api.dto.request.CreateDroneRequest;
import com.smartdroneinspection.assets.api.dto.request.UpdateDroneServiceabilityRequest;
import com.smartdroneinspection.assets.api.dto.response.DroneResponse;
import com.smartdroneinspection.assets.api.dto.response.DroneSelectionResponse;
import com.smartdroneinspection.assets.domain.Drone;
import com.smartdroneinspection.assets.domain.enums.DroneServiceability;
import com.smartdroneinspection.assets.repository.DroneRepository;
import com.smartdroneinspection.shared.exception.BusinessException;
import com.smartdroneinspection.subscriptions.SubscriptionEntitlement;
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
 * drone belonging to another organization is reported exactly like one that does not exist. Reads
 * stay available to an organization without an active subscription; inventory writes do not,
 * matching the workforce provisioning precedent.
 */
@Service
public class DroneService {

  private static final String NOT_FOUND_MESSAGE = "Drone not found.";

  private final DroneRepository drones;
  private final UserAccess userAccess;
  private final SubscriptionEntitlement entitlement;

  public DroneService(
      DroneRepository drones, UserAccess userAccess, SubscriptionEntitlement entitlement) {
    this.drones = drones;
    this.userAccess = userAccess;
    this.entitlement = entitlement;
  }

  @Transactional
  public DroneResponse create(UUID actorId, CreateDroneRequest request) {
    UUID organizationId = requireOrganization(requireActive(actorId));
    requireEntitled(organizationId);
    requireJsonDocument(
        request.payloadMetadata(), "Payload metadata must be a valid JSON document.");
    String serialNumber = request.serialNumber().trim();
    if (drones.existsByOrganizationIdAndSerialNumber(organizationId, serialNumber)) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "DUPLICATE_SERIAL_NUMBER", "Serial number already exists");
    }
    return DroneResponse.from(
        drones.saveAndFlush(
            new Drone(
                organizationId,
                serialNumber,
                request.model(),
                request.manufacturer(),
                request.payloadMetadata(),
                DroneServiceability.ACTIVE,
                null,
                null,
                request.notes(),
                request.registrationNumber(),
                request.registrationFileKey(),
                request.registrationExpiresAt())));
  }

  @Transactional(readOnly = true)
  public List<DroneResponse> list(UUID actorId) {
    UUID organizationId = requireOrganization(requireActive(actorId));
    return drones.findAllByOrganizationId(organizationId).stream()
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
    requireEntitled(drone.getOrganizationId());
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
    requireEntitled(drone.getOrganizationId());
    drone.updateRegistration(
        request.registrationNumber(),
        request.registrationFileKey(),
        request.registrationExpiresAt());
    return DroneResponse.from(drones.saveAndFlush(drone));
  }

  /**
   * Lists the organization's serviceable airframes. Serviceability is the only filter applied: this
   * query makes no assertion about permits or flight readiness, which are owned by other flows.
   */
  @Transactional(readOnly = true)
  public List<DroneSelectionResponse> selection(UUID actorId) {
    UUID organizationId = requireOrganization(requireActive(actorId));
    return drones
        .findAllByOrganizationIdAndServiceability(organizationId, DroneServiceability.ACTIVE)
        .stream()
        .map(DroneSelectionResponse::from)
        .toList();
  }

  private Drone requireOwnedDrone(UUID actorId, UUID droneId) {
    UUID organizationId = requireOrganization(requireActive(actorId));
    return drones
        .findByIdAndOrganizationId(droneId, organizationId)
        .orElseThrow(
            () ->
                new BusinessException(HttpStatus.NOT_FOUND, "DRONE_NOT_FOUND", NOT_FOUND_MESSAGE));
  }

  private void requireEntitled(UUID organizationId) {
    entitlement.requireActive(organizationId, Instant.now());
  }

  /** Rejects malformed JSON before it reaches the PostgreSQL jsonb column as an opaque 500. */
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

  private UUID requireOrganization(UserAccess.ActiveUser actor) {
    if (actor.organizationId() == null) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Actor has no organization scope");
    }
    return actor.organizationId();
  }

  private UserAccess.ActiveUser requireActive(UUID actorId) {
    return userAccess
        .findActiveUser(actorId)
        .orElseThrow(
            () -> new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "User is not active"));
  }
}
