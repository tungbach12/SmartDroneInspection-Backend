package com.smartdroneinspection.inspections.api.dto.response;

import com.smartdroneinspection.inspections.domain.enums.InspectionStatus;
import java.time.Instant;
import java.util.UUID;

public record StartInspectionResponse(
    UUID inspectionId,
    UUID assignmentId,
    UUID serviceOrderId,
    UUID assetId,
    UUID checklistTemplateId,
    InspectionStatus status,
    Instant startedAt) {}
