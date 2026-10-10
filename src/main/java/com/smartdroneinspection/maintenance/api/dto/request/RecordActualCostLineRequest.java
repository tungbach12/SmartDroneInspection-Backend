package com.smartdroneinspection.maintenance.api.dto.request;

import com.smartdroneinspection.maintenance.domain.enums.CostLineKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * MF4-19: an actual cost line recorded during reconciliation.
 *
 * <p>These are written with state ACTUAL and never overwrite an estimate line. The amount is
 * derived by the SYSTEM from quantity and unit rate.
 */
public record RecordActualCostLineRequest(
    @NotNull UUID taskId,
    @NotNull CostLineKind lineKind,
    @NotBlank String description,
    @NotNull BigDecimal quantity,
    String unit,
    @NotNull BigDecimal unitRate,
    @NotBlank String currency,
    String taxTreatment,
    String evidenceReference) {}
