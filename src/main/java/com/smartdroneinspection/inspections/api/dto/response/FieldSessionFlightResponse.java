package com.smartdroneinspection.inspections.api.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record FieldSessionFlightResponse(
    UUID id,
    UUID fieldSessionId,
    UUID inspectionId,
    int flightNumber,
    LocalDate flightDate,
    String flightLabel,
    String telemetrySource,
    Instant startedAt,
    Instant endedAt,
    Integer flightDurationSeconds,
    BigDecimal distanceFlownMeters,
    BigDecimal maxAltitudeMeters,
    Integer startBatteryPercent,
    Integer endBatteryPercent,
    String flightFinalState,
    String flightReportNote,
    UUID flightVideoEvidenceId,
    boolean hasFlightReport,
    Instant createdAt) {}
