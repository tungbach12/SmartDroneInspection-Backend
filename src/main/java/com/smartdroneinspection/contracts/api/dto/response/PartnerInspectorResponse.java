package com.smartdroneinspection.contracts.api.dto.response;

import com.smartdroneinspection.contracts.service.PartnerRegistryService;
import java.time.Instant;
import java.util.UUID;

public record PartnerInspectorResponse(UUID id, String fullName, Instant licenseExpiresAt) {
  public static PartnerInspectorResponse from(PartnerRegistryService.InspectorOption option) {
    return new PartnerInspectorResponse(option.id(), option.fullName(), option.licenseExpiresAt());
  }
}
