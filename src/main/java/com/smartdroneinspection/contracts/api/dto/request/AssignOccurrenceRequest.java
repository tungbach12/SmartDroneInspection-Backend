package com.smartdroneinspection.contracts.api.dto.request;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public record AssignOccurrenceRequest(
    @NotNull Instant plannedStartAt,
    @NotNull Instant plannedEndAt,
    @NotNull UUID inspectorId,
    @NotNull UUID droneId) {}
