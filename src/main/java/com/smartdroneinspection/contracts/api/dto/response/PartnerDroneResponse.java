package com.smartdroneinspection.contracts.api.dto.response;

import com.smartdroneinspection.contracts.service.PartnerRegistryService;
import java.math.BigDecimal;
import java.util.UUID;

public record PartnerDroneResponse(
    UUID id,
    UUID inspectorOwnerId,
    String serialNumber,
    String model,
    boolean hasImage,
    BigDecimal maxAltitudeMeters) {
  public static PartnerDroneResponse from(PartnerRegistryService.DroneOption option) {
    return new PartnerDroneResponse(
        option.id(),
        option.inspectorOwnerId(),
        option.serialNumber(),
        option.model(),
        option.hasImage(),
        option.maxAltitudeMeters());
  }
}
