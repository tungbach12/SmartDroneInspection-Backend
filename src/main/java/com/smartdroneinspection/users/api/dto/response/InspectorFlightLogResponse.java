package com.smartdroneinspection.users.api.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record InspectorFlightLogResponse(
    UUID id,
    UUID inspectorUserId,
    String flightCode,
    LocalDate flightDate,
    String missionTitle,
    String droneModel,
    String droneSerialNumber,
    String location,
    int flightDurationMinutes,
    double flightDurationHours,
    String inspectionType,
    boolean safetyIncident,
    String notes,
    UUID recordedByUserId,
    Instant createdAt) {}
