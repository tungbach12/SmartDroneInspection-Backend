package com.smartdroneinspection.assets.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record CreateDroneRequest(
    @NotBlank @Size(max = 128) String serialNumber,
    @Size(max = 200) String model,
    @Size(max = 200) String manufacturer,
    @Size(max = 2000) String payloadMetadata,
    @Size(max = 2000) String notes,
    @Size(max = 100) String registrationNumber,
    @Size(max = 1000) String registrationFileKey,
    Instant registrationExpiresAt) {

  public CreateDroneRequest(
      String serialNumber,
      String model,
      String manufacturer,
      String payloadMetadata,
      String notes) {
    this(serialNumber, model, manufacturer, payloadMetadata, notes, null, null, null);
  }
}
