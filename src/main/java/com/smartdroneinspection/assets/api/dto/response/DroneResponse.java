package com.smartdroneinspection.assets.api.dto.response;

import com.smartdroneinspection.assets.domain.Drone;
import java.time.Instant;
import java.util.UUID;

public record DroneResponse(
    UUID id,
    String serialNumber,
    String model,
    String manufacturer,
    String payloadMetadata,
    String serviceability,
    Instant lastMaintenanceAt,
    Instant nextMaintenanceAt,
    String notes,
    Instant createdAt) {

  public static DroneResponse from(Drone drone) {
    return new DroneResponse(
        drone.getId(),
        drone.getSerialNumber(),
        drone.getModel(),
        drone.getManufacturer(),
        drone.getPayloadMetadata(),
        drone.getServiceability().name(),
        drone.getLastMaintenanceAt(),
        drone.getNextMaintenanceAt(),
        drone.getNotes(),
        drone.getCreatedAt());
  }
}
