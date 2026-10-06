package com.smartdroneinspection.inspections.api.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateMissionPlanRequest(@NotNull UUID serviceOrderId) {}
