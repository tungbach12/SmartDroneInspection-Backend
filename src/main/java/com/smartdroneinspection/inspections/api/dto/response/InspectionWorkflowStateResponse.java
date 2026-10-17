package com.smartdroneinspection.inspections.api.dto.response;

import com.smartdroneinspection.inspections.domain.enums.InspectionStatus;
import java.time.Instant;
import java.util.UUID;

public record InspectionWorkflowStateResponse(
    InspectionStatus status,
    UUID findingsConfirmedByUserId,
    Instant findingsConfirmedAt,
    String findingsSnapshotHash) {}
