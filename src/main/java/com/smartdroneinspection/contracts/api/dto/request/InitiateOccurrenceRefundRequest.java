package com.smartdroneinspection.contracts.api.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record InitiateOccurrenceRefundRequest(
    @NotNull @DecimalMin("0.01") BigDecimal amount,
    @NotBlank @Size(max = 255) String requestReference,
    @NotBlank @Size(max = 2000) String reason,
    @NotNull @DecimalMin("0.00") BigDecimal inspectorReceivableAmount,
    @NotNull @DecimalMin("0.00") BigDecimal platformAbsorbedAmount) {}
