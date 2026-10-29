package com.smartdroneinspection.assets.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record SubmitDroneRegistrationApplicationRequest(
    @NotBlank @Size(max = 128) String serialNumber,
    @Size(max = 200) String model,
    @Size(max = 200) String manufacturer,
    @NotBlank @Size(max = 100) String registrationNumber,
    @NotBlank @Size(max = 1000) String registrationFileKey,
    @NotNull Instant registrationExpiresAt,
    @NotBlank @Size(max = 1000) String ownershipFileKey,
    @NotBlank @Size(max = 1000) String inspectionFileKey) {}
