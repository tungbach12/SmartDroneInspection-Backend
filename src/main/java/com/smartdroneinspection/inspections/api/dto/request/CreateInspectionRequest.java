package com.smartdroneinspection.inspections.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public record CreateInspectionRequest(
    @NotNull UUID assetId,
    @NotBlank @Size(max = 1000) String objective,
    String scope,
    String componentScope,
    String acceptanceCriteria,
    Instant plannedStartAt,
    Instant plannedEndAt,
    UUID inspectorId,
    UUID droneId,
    String reassignmentReason) {}
