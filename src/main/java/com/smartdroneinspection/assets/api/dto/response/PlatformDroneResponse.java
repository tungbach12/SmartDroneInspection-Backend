package com.smartdroneinspection.assets.api.dto.response;

import com.smartdroneinspection.assets.domain.PlatformDrone;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PlatformDroneResponse(
    UUID id,
    String serialNumber,
    String model,
    String manufacturer,
    String payloadMetadata,
    BigDecimal monthlyRateUsd,
    String status,
    String notes,
    Instant createdAt,
    Instant updatedAt) {

  public static PlatformDroneResponse from(PlatformDrone drone) {
    return new PlatformDroneResponse(
        drone.getId(),
        drone.getSerialNumber(),
        drone.getModel(),
        drone.getManufacturer(),
        drone.getPayloadMetadata(),
        drone.getMonthlyRateUsd(),
        drone.getStatus().name(),
        drone.getNotes(),
        drone.getCreatedAt(),
        drone.getUpdatedAt());
  }
}
