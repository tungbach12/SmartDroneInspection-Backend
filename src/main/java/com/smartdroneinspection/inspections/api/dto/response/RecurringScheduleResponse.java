package com.smartdroneinspection.inspections.api.dto.response;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

public record RecurringScheduleResponse(
    UUID id,
    UUID organizationId,
    UUID assetId,
    String assetName,
    String assetLocationText,
    UUID createdByUserId,
    String createdByName,
    UUID inspectorId,
    String inspectorName,
    UUID droneId,
    String droneSerialNumber,
    String droneModel,
    LocalDateTime anchor,
    String timezone,
    String unit,
    int interval,
    long durationSeconds,
    int reminderDays,
    Instant endsAt,
    String status,
    long nextOccurrenceIndex,
    Instant nextDueAt,
    long totalGeneratedOccurrences,
    long totalBlockedOccurrences,
    Instant createdAt,
    Instant updatedAt) {}
