package com.smartdroneinspection.users.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SavePilotLicenseClassRequest(
    @NotBlank @Size(max = 64) String name,
    @Size(max = 500) String description,
    @NotNull Boolean active) {}
