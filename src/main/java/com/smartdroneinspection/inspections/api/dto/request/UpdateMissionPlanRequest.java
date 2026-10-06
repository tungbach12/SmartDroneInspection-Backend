package com.smartdroneinspection.inspections.api.dto.request;

import java.math.BigDecimal;
import java.util.UUID;

public record UpdateMissionPlanRequest(
    String cameraModel,
    String droneRegistrationId,
    UUID pilotUserId,
    BigDecimal targetGsdMmPerPixel,
    BigDecimal plannedAglM,
    BigDecimal forwardOverlapPercent,
    BigDecimal sideOverlapPercent,
    BigDecimal sensorWidthMm,
    BigDecimal focalLengthMm,
    Integer imageWidthPx) {}
