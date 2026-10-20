package com.smartdroneinspection.inspections.api.dto.response;

import java.time.Instant;
import java.util.UUID;

public record InspectionResponse(
    UUID id,
    UUID organizationId,
    UUID assetId,
    String assetName,
    UUID assetPairAssignmentId,
    UUID inspectorId,
    String inspectorName,
    UUID droneId,
    String droneSerialNumber,
    String objective,
    String scope,
    String componentScope,
    String acceptanceCriteria,
    Instant plannedStartAt,
    Instant plannedEndAt,
    String status,
    Instant createdAt,
    Instant updatedAt) {}
