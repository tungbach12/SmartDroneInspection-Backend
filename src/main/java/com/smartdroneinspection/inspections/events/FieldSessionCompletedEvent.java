package com.smartdroneinspection.inspections.events;

import java.time.Instant;
import java.util.UUID;

/** Domain event emitted when a field session finishes and completes in an inspection. */
public record FieldSessionCompletedEvent(
    UUID inspectionId,
    UUID sessionId,
    UUID organizationId,
    UUID inspectorId,
    UUID droneId,
    UUID assetId,
    Instant startedAt,
    Instant endedAt,
    long durationMinutes,
    String inspectionType,
    String outcomeNotes,
    String missionTitle,
    String location,
    String assetCategory,
    String droneModel,
    String droneSerialNumber) {}
