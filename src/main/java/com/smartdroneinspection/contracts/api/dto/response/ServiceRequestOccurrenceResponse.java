package com.smartdroneinspection.contracts.api.dto.response;

import com.smartdroneinspection.contracts.domain.ServiceRequestOccurrence;
import com.smartdroneinspection.contracts.domain.ServiceRequestOccurrence.OccurrenceStatus;
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
    Instant assignedAt) {
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
        occurrence.getAssignedAt());
  }
}
