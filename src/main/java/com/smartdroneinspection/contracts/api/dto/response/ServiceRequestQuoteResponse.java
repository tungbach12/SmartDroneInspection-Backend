package com.smartdroneinspection.contracts.api.dto.response;

import com.smartdroneinspection.contracts.domain.ServiceRequestQuote;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ServiceRequestQuoteResponse(
    UUID id,
    String scopeSnapshot,
    Instant firstOccurrenceStartAt,
    Instant firstOccurrenceEndAt,
    UUID inspectorId,
    UUID droneId,
    String currency,
    BigDecimal pricePerOccurrenceVnd,
    BigDecimal pricePerOccurrenceUsd,
    UUID issuedByUserId,
    Instant createdAt,
    Instant updatedAt,
    UUID updatedByUserId,
    long rowVersion,
    List<String> requiredDroneFeatureCodes) {

  public ServiceRequestQuoteResponse {
    requiredDroneFeatureCodes =
        requiredDroneFeatureCodes == null ? List.of() : List.copyOf(requiredDroneFeatureCodes);
  }

  public static ServiceRequestQuoteResponse from(ServiceRequestQuote quote) {
    return new ServiceRequestQuoteResponse(
        quote.getId(),
        quote.getScopeSnapshot(),
        quote.getFirstOccurrenceStartAt(),
        quote.getFirstOccurrenceEndAt(),
        quote.getInspectorId(),
        quote.getDroneId(),
        quote.getCurrency(),
        quote.getPricePerOccurrenceVnd(),
        quote.getPricePerOccurrenceUsd(),
        quote.getIssuedByUserId(),
        quote.getCreatedAt(),
        quote.getUpdatedAt(),
        quote.getUpdatedByUserId(),
        quote.getRowVersion(),
        quote.getRequiredDroneFeatureCodes());
  }
}
