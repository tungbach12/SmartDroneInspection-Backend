package com.smartdroneinspection.assets.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateDroneRequest(
    @NotBlank @Size(max = 128) String serialNumber,
    @Size(max = 200) String model,
    @Size(max = 200) String manufacturer,
    @Size(max = 2000) String payloadMetadata,
    @Size(max = 2000) String notes) {}
