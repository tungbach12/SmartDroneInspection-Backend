package com.smartdroneinspection.contracts.api.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record RecordInspectorReceivableRecoveryRequest(
    @NotNull @DecimalMin("0.01") BigDecimal amount,
    @NotBlank @Size(max = 255) String externalReference,
    @NotBlank @Size(max = 2000) String note) {}
