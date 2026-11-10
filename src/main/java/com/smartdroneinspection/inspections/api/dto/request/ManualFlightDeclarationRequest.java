package com.smartdroneinspection.inspections.api.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ManualFlightDeclarationRequest(
    @NotBlank String reasonCode,
    @NotNull @Min(1) @Max(720) Integer estimatedDurationMinutes,
    @Min(0) @Max(10000) Double maxAltitudeMeters,
    @NotBlank @Size(max = 900) String notes) {}
