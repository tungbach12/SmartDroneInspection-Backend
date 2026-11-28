package com.smartdroneinspection.contracts.api.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdatePlatformOperationsSettingsRequest(
    @NotNull @Min(1) @Max(720) Integer assignmentAcceptanceTimeoutHours,
    @Min(0) @Max(365) Integer serviceRequestLeadDays,
    @Min(10) @Max(600) Integer apiRequestTimeoutSeconds) {}
