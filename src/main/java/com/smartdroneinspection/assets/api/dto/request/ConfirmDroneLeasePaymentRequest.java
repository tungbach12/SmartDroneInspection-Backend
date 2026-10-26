package com.smartdroneinspection.assets.api.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

public record ConfirmDroneLeasePaymentRequest(
    @NotBlank @Size(max = 128) String externalReference,
    Instant startsAt,
    Instant endsAt,
    @DecimalMin(value = "0.01") BigDecimal agreedAmountUsd,
    @Size(max = 2000) String notes) {}
