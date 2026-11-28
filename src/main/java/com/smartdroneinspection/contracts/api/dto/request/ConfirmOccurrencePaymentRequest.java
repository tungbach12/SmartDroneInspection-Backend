package com.smartdroneinspection.contracts.api.dto.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record ConfirmOccurrencePaymentRequest(
    @NotBlank @Size(max = 255) String paymentReference,
    @NotNull @DecimalMin("0.01") @DecimalMax("100000000000.00") BigDecimal amount,
    @Size(max = 2000) String note) {}
