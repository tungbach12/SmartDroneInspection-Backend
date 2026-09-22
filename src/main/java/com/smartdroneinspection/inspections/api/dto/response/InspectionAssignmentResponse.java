package com.smartdroneinspection.inspections.api.dto.response;

import com.smartdroneinspection.inspectionrequests.domain.enums.InspectionAssignmentStatus;
import java.time.Instant;
import java.util.UUID;

public record InspectionAssignmentResponse(
    UUID assignmentId,
    UUID serviceOrderId,
    UUID assetId,
    Instant deadline,
    InspectionAssignmentStatus status,
    UUID inspectionId) {}
