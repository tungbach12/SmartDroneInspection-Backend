package com.smartdroneinspection.contracts.api.dto.response;

import com.smartdroneinspection.contracts.domain.ServiceRequestQuote;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ServiceRequestQuoteResponse(
    UUID id,
    int versionNo,
    String scopeSnapshot,
    Instant firstOccurrenceStartAt,
    Instant firstOccurrenceEndAt,
    UUID inspectorId,
    UUID droneId,
    BigDecimal pricePerOccurrenceUsd,
    UUID issuedByUserId,
    Instant createdAt) {

  public static ServiceRequestQuoteResponse from(ServiceRequestQuote quote) {
    return new ServiceRequestQuoteResponse(
        quote.getId(),
        quote.getVersionNo(),
        quote.getScopeSnapshot(),
        quote.getFirstOccurrenceStartAt(),
        quote.getFirstOccurrenceEndAt(),
        quote.getInspectorId(),
        quote.getDroneId(),
        quote.getPricePerOccurrenceUsd(),
        quote.getIssuedByUserId(),
        quote.getCreatedAt());
  }
}
