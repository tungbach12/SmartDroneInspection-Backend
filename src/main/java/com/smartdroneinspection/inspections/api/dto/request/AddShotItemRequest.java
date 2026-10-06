package com.smartdroneinspection.inspections.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record AddShotItemRequest(
    @Positive int sequenceNumber,
    @NotBlank String componentReference,
    BigDecimal waypointLatitude,
    BigDecimal waypointLongitude,
    BigDecimal waypointAltitudeM,
    BigDecimal cameraHeadingDegrees,
    BigDecimal gimbalPitchDegrees,
    BigDecimal targetGsdMmPerPixel,
    String captureInstructions) {}
