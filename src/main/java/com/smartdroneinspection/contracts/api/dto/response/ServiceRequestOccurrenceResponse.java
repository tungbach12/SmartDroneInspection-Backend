package com.smartdroneinspection.contracts.api.dto.response;

import com.smartdroneinspection.contracts.domain.ServiceRequestOccurrence;
import com.smartdroneinspection.contracts.domain.ServiceRequestOccurrence.OccurrencePaymentStatus;
import com.smartdroneinspection.contracts.domain.ServiceRequestOccurrence.OccurrencePayoutStatus;
import com.smartdroneinspection.contracts.domain.ServiceRequestOccurrence.OccurrenceStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ServiceRequestOccurrenceResponse(
    UUID id,
    int occurrenceIndex,
    Instant plannedStartAt,
    Instant plannedEndAt,
    OccurrenceStatus status,
    UUID inspectorId,
    UUID droneId,
    UUID inspectionId,
    Instant assignedAt,
    String contractFileName,
    String contractContentType,
    Long contractSizeBytes,
    Instant contractUploadedAt,
    String currency,
    BigDecimal totalPrice,
    BigDecimal vatAmount,
    OccurrencePaymentStatus paymentStatus,
    Instant paymentConfirmedAt,
    OccurrencePayoutStatus payoutStatus,
    Instant reportAcceptedAt) {

  public static ServiceRequestOccurrenceResponse from(ServiceRequestOccurrence occurrence) {
    return new ServiceRequestOccurrenceResponse(
        occurrence.getId(),
        occurrence.getOccurrenceIndex(),
        occurrence.getPlannedStartAt(),
        occurrence.getPlannedEndAt(),
        occurrence.getStatus(),
        occurrence.getInspectorId(),
        occurrence.getDroneId(),
        occurrence.getInspectionId(),
        occurrence.getAssignedAt(),
        occurrence.getContractFileName(),
        occurrence.getContractContentType(),
        occurrence.getContractSizeBytes(),
        occurrence.getContractUploadedAt(),
        occurrence.getCurrency(),
        occurrence.getTotalPrice(),
        occurrence.getVatAmount(),
        occurrence.getPaymentStatus(),
        occurrence.getPaymentConfirmedAt(),
        occurrence.getPayoutStatus(),
        occurrence.getReportAcceptedAt());
  }
}
