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

/**
 * Quote preparation input. The partner Inspector and drone are deliberately absent: they are chosen
 * at dispatch (step 4) and confirmed by the Inspector, not fixed by the commercial terms.
 */
public record IssueServiceQuoteRequest(
    @NotBlank @Size(max = 4000) String scopeSnapshot,
    @NotNull Instant firstOccurrenceStartAt,
    @NotNull Instant firstOccurrenceEndAt,
    @DecimalMin(value = "0.01") BigDecimal pricePerOccurrenceVnd,
    @DecimalMin(value = "0.01") BigDecimal pricePerOccurrenceUsd,
    List<@NotBlank @Size(max = 64) String> requiredDroneFeatureCodes) {

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

  public IssueServiceQuoteRequest(
      String scopeSnapshot,
      Instant firstOccurrenceStartAt,
      Instant firstOccurrenceEndAt,
      BigDecimal pricePerOccurrenceUsd) {
    this(scopeSnapshot, firstOccurrenceStartAt, firstOccurrenceEndAt, null, pricePerOccurrenceUsd);
  }

  public IssueServiceQuoteRequest(
      String scopeSnapshot,
      Instant firstOccurrenceStartAt,
      Instant firstOccurrenceEndAt,
      BigDecimal pricePerOccurrenceVnd,
      BigDecimal pricePerOccurrenceUsd) {
    this(
        scopeSnapshot,
        firstOccurrenceStartAt,
        firstOccurrenceEndAt,
        pricePerOccurrenceVnd,
        pricePerOccurrenceUsd,
        List.of());
  }

  public String resolvedCurrency() {
    return pricePerOccurrenceVnd != null ? "VND" : "USD";
  }

  public BigDecimal resolvedPrice() {
    return pricePerOccurrenceVnd != null ? pricePerOccurrenceVnd : pricePerOccurrenceUsd;
  }
}
