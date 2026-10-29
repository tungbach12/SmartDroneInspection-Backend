package com.smartdroneinspection.contracts.api.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record IssueServiceQuoteRequest(
    @NotBlank @Size(max = 4000) String scopeSnapshot,
    @NotNull Instant firstOccurrenceStartAt,
    @NotNull Instant firstOccurrenceEndAt,
    @NotNull UUID inspectorId,
    @NotNull UUID droneId,
    @NotNull @DecimalMin(value = "0.01") BigDecimal pricePerOccurrenceUsd) {}
