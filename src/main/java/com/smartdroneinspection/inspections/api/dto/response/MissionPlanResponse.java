package com.smartdroneinspection.inspections.api.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record MissionPlanResponse(
    UUID id,
    UUID serviceOrderId,
    UUID providerId,
    int versionNumber,
    UUID previousVersionId,
    UUID createdByUserId,
    String droneRegistrationId,
    UUID pilotUserId,
    String flightPermitReference,
    String cameraModel,
    BigDecimal targetGsdMmPerPixel,
    BigDecimal plannedAglM,
    BigDecimal forwardOverlapPercent,
    BigDecimal sideOverlapPercent,
    String airspaceCheckStatus,
    String status,
    UUID approvedByUserId,
    java.time.Instant approvedAt,
    java.time.Instant createdAt,
    java.time.Instant updatedAt,
    List<MissionShotItemResponse> shotItems) {}
