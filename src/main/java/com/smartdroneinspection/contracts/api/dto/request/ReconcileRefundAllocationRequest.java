package com.smartdroneinspection.contracts.api.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record ReconcileRefundAllocationRequest(
    @NotNull @DecimalMin("0.00") BigDecimal inspectorReceivableAmount,
    @NotNull @DecimalMin("0.00") BigDecimal platformAbsorbedPlanAmount,
    @NotNull @DecimalMin("0.00") BigDecimal inspectorPayoutReduction,
    @NotNull @DecimalMin("0.00") BigDecimal platformCommissionAdjustment,
    @NotNull @DecimalMin("0.00") BigDecimal vatAdjustment,
    @NotNull @DecimalMin("0.00") BigDecimal tncnAdjustment,
    @NotNull @DecimalMin("0.00") BigDecimal platformAbsorbedAmount,
    @NotBlank @Size(max = 255) String reconciliationReference,
    @NotBlank @Size(max = 2000) String note) {}
