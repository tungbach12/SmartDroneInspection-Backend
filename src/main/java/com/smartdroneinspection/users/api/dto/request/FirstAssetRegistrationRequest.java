package com.smartdroneinspection.users.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record FirstAssetRegistrationRequest(
    @NotBlank @Size(max = 64) String code,
    @NotBlank @Size(max = 200) String name,
    @Size(max = 2000) String description,
    @NotNull UUID categoryId,
    @NotBlank @Size(max = 500) String locationText,
    @NotNull BigDecimal latitude,
    @NotNull BigDecimal longitude,
    @Size(max = 1000) String ownershipInformation) {}
