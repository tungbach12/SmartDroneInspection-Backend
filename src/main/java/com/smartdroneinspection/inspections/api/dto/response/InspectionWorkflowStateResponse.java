package com.smartdroneinspection.inspections.api.dto.response;

import com.smartdroneinspection.inspections.domain.enums.InspectionStatus;
import java.time.Instant;

public record InspectionWorkflowStateResponse(
    InspectionStatus status, Instant findingsConfirmedAt, String findingsSnapshotHash) {}
