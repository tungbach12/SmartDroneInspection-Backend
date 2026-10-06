package com.smartdroneinspection.inspections.api.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record MissionShotItemResponse(
    UUID id,
    int sequenceNumber,
    String componentReference,
    BigDecimal waypointLatitude,
    BigDecimal waypointLongitude,
    BigDecimal waypointAltitudeM,
    BigDecimal cameraHeadingDegrees,
    BigDecimal gimbalPitchDegrees,
    BigDecimal targetGsdMmPerPixel,
    String captureInstructions) {}
