package com.smartdroneinspection.assets.api.dto.response;

import com.smartdroneinspection.assets.domain.Drone;
import com.smartdroneinspection.users.UserAccess;
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
    String inspectorEmail) {

  public static InspectorDroneResponse from(Drone drone) {
    return from(drone, null);
  }

  public static InspectorDroneResponse from(Drone drone, UserAccess.ActiveUser inspector) {
    return new InspectorDroneResponse(
        drone.getId(),
        drone.getSerialNumber(),
        drone.getModel(),
        drone.getManufacturer(),
        drone.getServiceability().name(),
        drone.getRegistrationNumber(),
        drone.getRegistrationExpiresAt(),
        drone.getInspectorOwnerId(),
        inspector != null ? inspector.fullName() : null,
        inspector != null ? inspector.email() : null);
  }
}
