package com.smartdroneinspection.assets.api.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateDroneLeaseRequest(
    @NotNull UUID platformDroneId,
    @NotNull @Min(1) @Max(60) Integer leasePeriodMonths,
    @Size(max = 2000) String notes) {}
