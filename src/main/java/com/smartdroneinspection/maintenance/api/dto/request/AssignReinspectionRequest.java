package com.smartdroneinspection.maintenance.api.dto.request;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public record AssignReinspectionRequest(
    @NotNull UUID inspectorUserId,
    @NotNull UUID droneId,
    @NotNull Instant plannedStartAt,
    @NotNull Instant plannedEndAt) {}
