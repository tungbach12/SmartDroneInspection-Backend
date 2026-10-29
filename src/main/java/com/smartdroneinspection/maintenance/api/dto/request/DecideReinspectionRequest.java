package com.smartdroneinspection.maintenance.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record DecideReinspectionRequest(
    @NotBlank @Pattern(regexp = "ACCEPTED|REPAIR_REQUIRED") String decision,
    @Size(max = 4000) String note) {}
