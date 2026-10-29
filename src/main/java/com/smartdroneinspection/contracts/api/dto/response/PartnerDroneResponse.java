package com.smartdroneinspection.contracts.api.dto.response;

import com.smartdroneinspection.contracts.service.PartnerRegistryService;
import java.util.UUID;

public record PartnerDroneResponse(
    UUID id, String serialNumber, String model, UUID partnerOrganizationId) {
  public static PartnerDroneResponse from(PartnerRegistryService.DroneOption option) {
    return new PartnerDroneResponse(
        option.id(), option.serialNumber(), option.model(), option.partnerOrganizationId());
  }
}
