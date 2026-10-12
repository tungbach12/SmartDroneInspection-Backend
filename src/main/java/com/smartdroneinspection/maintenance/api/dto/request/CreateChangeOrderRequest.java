package com.smartdroneinspection.maintenance.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.time.Instant;

/** MF4-12: a proposal to widen scope, cost or time. It authorizes nothing until approved. */
public record CreateChangeOrderRequest(
    @NotBlank String reason,
    String affectedTasks,
    BigDecimal proposedDelta,
    String supportingEvidence,
    Instant proposedStartAt,
    Instant proposedEndAt) {}
