package com.smartdroneinspection.assets.api.dto.response;

import com.smartdroneinspection.assets.domain.Drone;
import com.smartdroneinspection.users.UserAccess;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public record InspectorDroneResponse(
    UUID id,
    String serialNumber,
    String model,
    String manufacturer,
    String serviceability,
    String registrationNumber,
    Instant registrationExpiresAt,
    UUID inspectorOwnerId,
    String inspectorFullName,
    String inspectorEmail,
    Double mtowKg,
    String legalCategory,
    String requiredLicenseClass,
    String registrationExpirationStatus,
    Long registrationRemainingDays) {

  public static InspectorDroneResponse from(Drone drone) {
    return from(drone, null);
  }

  public static InspectorDroneResponse from(Drone drone, UserAccess.ActiveUser inspector) {
    Instant expiresAt = drone.getRegistrationExpiresAt();
    Long remainingDays =
        expiresAt != null ? Duration.between(Instant.now(), expiresAt).toDays() : null;
    String expirationStatus =
        remainingDays == null
            ? "UNKNOWN"
            : remainingDays < 0 ? "EXPIRED" : remainingDays <= 30 ? "EXPIRING_SOON" : "VALID";
    return new InspectorDroneResponse(
        drone.getId(),
        drone.getSerialNumber(),
        drone.getModel(),
        drone.getManufacturer(),
        drone.getServiceability().name(),
        drone.getRegistrationNumber(),
        expiresAt,
        drone.getInspectorOwnerId(),
        inspector != null ? inspector.fullName() : null,
        inspector != null ? inspector.email() : null,
        drone.resolveMtowKg(),
        drone.resolveLegalCategory(),
        drone.requiresClassBLicense() ? "CLASS_B" : "CLASS_A",
        expirationStatus,
        remainingDays);
  }
}
