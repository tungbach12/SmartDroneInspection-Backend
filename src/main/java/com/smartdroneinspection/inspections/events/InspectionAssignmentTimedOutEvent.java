package com.smartdroneinspection.inspections.events;

import java.time.Instant;
import java.util.UUID;

/** Emitted when a pending Inspector assignment expires without confirmation and is cancelled. */
public record InspectionAssignmentTimedOutEvent(
    UUID inspectionId,
    UUID ownerCustomerId,
    UUID inspectorId,
    UUID requestId,
    UUID assignedManagerId,
    int timeoutHours,
    Instant timedOutAt) {}
