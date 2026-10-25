package com.smartdroneinspection.assets.api.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreatePlatformDroneRequest(
    @NotBlank @Size(max = 128) String serialNumber,
    @NotBlank @Size(max = 200) String model,
    @Size(max = 200) String manufacturer,
    String payloadMetadata,
    @NotNull @DecimalMin(value = "0.01") BigDecimal monthlyRateUsd,
    @Size(max = 2000) String notes) {}
