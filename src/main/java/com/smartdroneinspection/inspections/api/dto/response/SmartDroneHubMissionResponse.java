package com.smartdroneinspection.inspections.api.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record SmartDroneHubMissionResponse(
    UUID inspectionId,
    UUID fieldSessionId,
    String missionState,
    String telemetrySource,
    boolean connected,
    Instant lastUpdateAt,
    BigDecimal latitude,
    BigDecimal longitude,
    Double altitudeMeters,
    Double groundSpeedMps,
    Double headingDeg,
    Integer batteryPercent,
    String flightMode,
    Boolean armed,
    Integer currentWaypoint,
    double distanceFlownMeters) {}
