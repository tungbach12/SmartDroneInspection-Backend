package com.smartdroneinspection.contracts.api.dto.response;

import com.smartdroneinspection.contracts.domain.ServiceRequest;
import com.smartdroneinspection.contracts.domain.ServiceRequest.RequestType;
import com.smartdroneinspection.contracts.domain.ServiceRequest.ServiceRequestStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ServiceRequestResponse(
    UUID id,
    UUID ownerCustomerId,
    UUID assetId,
    String description,
    LocalDate requestedFrom,
    LocalDate requestedUntil,
    String timezone,
    LocalTime windowStart,
    LocalTime windowEnd,
    RequestType requestType,
    String recurrenceUnit,
    Integer recurrenceInterval,
    Integer occurrenceCount,
    LocalDate recurrenceEndDate,
    ServiceRequestStatus status,
    UUID acceptedQuoteId,
    Instant acceptedAt,
    UUID acceptedByUserId,
    Map<String, Object> acceptedTermsSnapshot,
    String contractFileName,
    String contractContentType,
    Long contractSizeBytes,
    Instant contractUploadedAt,
    Instant createdAt,
    ServiceRequestQuoteResponse currentQuote,
    List<ServiceRequestOccurrenceResponse> occurrences,
    List<ServiceRequestMessageResponse> messages) {

  public static ServiceRequestResponse from(
      ServiceRequest request,
      ServiceRequestQuoteResponse currentQuote,
      List<ServiceRequestOccurrenceResponse> occurrences,
      List<ServiceRequestMessageResponse> messages) {
    return new ServiceRequestResponse(
        request.getId(),
        request.getOwnerCustomerId(),
        request.getAssetId(),
        request.getDescription(),
        request.getRequestedFrom(),
        request.getRequestedUntil(),
        request.getTimezone(),
        request.getWindowStart(),
        request.getWindowEnd(),
        request.getRequestType(),
        request.getRecurrenceUnit(),
        request.getRecurrenceInterval(),
        request.getOccurrenceCount(),
        request.getRecurrenceEndDate(),
        request.getStatus(),
        request.getAcceptedQuoteId(),
        request.getAcceptedAt(),
        request.getAcceptedByUserId(),
        request.getAcceptedTermsSnapshot(),
        request.getContractFileName(),
        request.getContractContentType(),
        request.getContractSizeBytes(),
        request.getContractUploadedAt(),
        request.getCreatedAt(),
        currentQuote,
        List.copyOf(occurrences),
        List.copyOf(messages));
  }
}
