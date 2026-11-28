package com.smartdroneinspection.assets.api.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Extracted drone registration and specification draft values returned by auto-fill. */
public record DroneSpecExtractionResponse(
    String model,
    String manufacturer,
    String serialNumber,
    String registrationNumber,
    LocalDate registrationExpiresAt,
    BigDecimal mtowKg,
    Integer maxFlightTimeMinutes,
    BigDecimal maxWindResistanceMps,
    BigDecimal maxAltitudeMeters,
    String opticalZoomLevel,
    String cameraSensor,
    List<String> features,
    boolean visionEnabled,
    String extractionSource,
    String matchedPresetModel,
    List<String> suggestedFields,
    List<String> notices) {}
