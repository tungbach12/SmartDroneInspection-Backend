package com.smartdroneinspection.inspections.api.dto.response;

import com.smartdroneinspection.inspections.spi.SmartDroneHubPort.FlightTracePoint;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SmartDroneHubFlightReportResponse(
    UUID inspectionId,
    UUID fieldSessionId,
    String telemetrySource,
    Instant startedAt,
    Instant endedAt,
    int durationSeconds,
    double distanceFlownMeters,
    Double maxAltitudeMeters,
    Integer startBatteryPercent,
    Integer endBatteryPercent,
    String finalState,
    String note,
    List<FlightTracePoint> flightTrace,
    UUID flightVideoEvidenceId) {}
