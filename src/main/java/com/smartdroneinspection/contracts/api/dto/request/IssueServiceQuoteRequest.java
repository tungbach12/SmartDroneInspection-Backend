package com.smartdroneinspection.contracts.api.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public record IssueServiceQuoteRequest(
    @NotBlank @Size(max = 4000) String scopeSnapshot,
    @NotNull Instant firstOccurrenceStartAt,
    @NotNull Instant firstOccurrenceEndAt,
    @NotNull UUID inspectorId,
    @NotNull UUID droneId,
    @DecimalMin(value = "0.01") BigDecimal pricePerOccurrenceVnd,
    @DecimalMin(value = "0.01") BigDecimal pricePerOccurrenceUsd,
    List<@NotBlank @Size(max = 64) String> requiredDroneFeatureCodes) {

  public IssueServiceQuoteRequest(
      String scopeSnapshot,
      Instant firstOccurrenceStartAt,
      Instant firstOccurrenceEndAt,
      UUID inspectorId,
      UUID droneId,
      BigDecimal pricePerOccurrenceUsd) {
    this(
        scopeSnapshot,
        firstOccurrenceStartAt,
        firstOccurrenceEndAt,
        inspectorId,
        droneId,
        null,
        pricePerOccurrenceUsd,
        List.of());
  }

  public IssueServiceQuoteRequest(
      String scopeSnapshot,
      Instant firstOccurrenceStartAt,
      Instant firstOccurrenceEndAt,
      UUID inspectorId,
      UUID droneId,
      BigDecimal pricePerOccurrenceVnd,
      BigDecimal pricePerOccurrenceUsd) {
    this(
        scopeSnapshot,
        firstOccurrenceStartAt,
        firstOccurrenceEndAt,
        inspectorId,
        droneId,
        pricePerOccurrenceVnd,
        pricePerOccurrenceUsd,
        List.of());
  }

  public IssueServiceQuoteRequest {
    requiredDroneFeatureCodes =
        requiredDroneFeatureCodes == null
            ? List.of()
            : Collections.unmodifiableList(new ArrayList<>(requiredDroneFeatureCodes));
    if ((pricePerOccurrenceVnd == null || pricePerOccurrenceVnd.signum() <= 0)
        && (pricePerOccurrenceUsd == null || pricePerOccurrenceUsd.signum() <= 0)) {
      throw new IllegalArgumentException("A valid positive price per occurrence must be provided");
    }
  }

  public String resolvedCurrency() {
    return pricePerOccurrenceVnd != null ? "VND" : "USD";
  }

  public BigDecimal resolvedPrice() {
    return pricePerOccurrenceVnd != null ? pricePerOccurrenceVnd : pricePerOccurrenceUsd;
  }
}
