package com.smartdroneinspection.maintenance.api.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record CreateChangeRequestPayload(
    @NotNull UUID workLogId,
    @NotBlank @Size(max = 2000) String reason,
    @NotBlank @Size(max = 4000) String additionalScope,
    @NotNull @DecimalMin("0.0") BigDecimal estimatedCostDelta) {}
