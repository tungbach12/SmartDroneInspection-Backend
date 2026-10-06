package com.smartdroneinspection.inspections.api.dto.request;

import com.smartdroneinspection.inspections.domain.enums.AirspaceCheckStatus;
import jakarta.validation.constraints.NotNull;

public record AirspaceCheckRequest(@NotNull AirspaceCheckStatus status, String notes) {}
