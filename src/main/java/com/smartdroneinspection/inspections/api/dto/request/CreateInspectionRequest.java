package com.smartdroneinspection.inspections.api.dto.request;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public record CreateInspectionRequest(
    @NotNull UUID assetId,
    @NotNull UUID inspectorId,
    @NotNull UUID droneId,
    @NotNull Instant plannedStartAt,
    @NotNull Instant plannedEndAt) {}
