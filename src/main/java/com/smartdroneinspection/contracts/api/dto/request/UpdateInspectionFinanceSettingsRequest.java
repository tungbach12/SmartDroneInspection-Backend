package com.smartdroneinspection.contracts.api.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record UpdateInspectionFinanceSettingsRequest(
    @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal commissionRate,
    @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal vatRate,
    @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal tncnRate) {}
