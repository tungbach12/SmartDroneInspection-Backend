package com.smartdroneinspection.inspections.api.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record UpdatePlatformAiSettingsRequest(
    @NotNull Boolean autoConfirmEnabled,
    @NotNull @DecimalMin("0.0") @DecimalMax("100.0") BigDecimal autoConfirmThreshold,
    Boolean applyToPending) {}
