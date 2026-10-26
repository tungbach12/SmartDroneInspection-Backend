package com.smartdroneinspection.inspections.api.dto.response;

import java.time.Instant;
import java.util.UUID;

public record ScheduleOccurrenceResponse(
    UUID id,
    UUID scheduleId,
    long occurrenceIndex,
    String occurrenceKey,
    Instant plannedStartAt,
    Instant plannedEndAt,
    String status,
    UUID inspectionId,
    String blockedReason,
    int retryCount,
    Instant createdAt,
    Instant updatedAt) {}
