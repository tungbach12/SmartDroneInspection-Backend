package com.smartdroneinspection.inspections.api.dto.request;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public record RescheduleInspectionRequest(
    @NotNull Instant plannedStartAt, @NotNull Instant plannedEndAt) {}
