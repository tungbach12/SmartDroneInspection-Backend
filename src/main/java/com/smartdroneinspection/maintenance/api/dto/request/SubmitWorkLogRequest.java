package com.smartdroneinspection.maintenance.api.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SubmitWorkLogRequest(
    @NotNull UUID executionAssignmentId,
    @NotNull Instant startedAt,
    Instant endedAt,
    @NotNull @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal progressPercent,
    @NotBlank @Size(max = 4000) String workSummary,
    @NotBlank String materialsUsed,
    @NotNull @DecimalMin("0.0") BigDecimal laborHours,
    BigDecimal actualCost,
    String currency,
    @NotNull UUID beforeEvidenceId,
    @NotNull UUID afterEvidenceId) {}
