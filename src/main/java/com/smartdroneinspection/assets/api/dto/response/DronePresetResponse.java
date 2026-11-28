package com.smartdroneinspection.assets.api.dto.response;

import java.math.BigDecimal;
import java.util.List;

/** Pre-validated airframe manufacturer specification preset for fast form pre-fill. */
public record DronePresetResponse(
    String id,
    String model,
    String manufacturer,
    BigDecimal mtowKg,
    Integer maxFlightTimeMinutes,
    BigDecimal maxWindResistanceMps,
    BigDecimal maxAltitudeMeters,
    String opticalZoomLevel,
    String cameraSensor,
    List<String> features) {}
