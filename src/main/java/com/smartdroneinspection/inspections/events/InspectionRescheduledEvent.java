package com.smartdroneinspection.inspections.events;

import java.time.Instant;
import java.util.UUID;

/** Domain event emitted when a manager reschedules a scheduled inspection window. */
public record InspectionRescheduledEvent(
    UUID inspectionId,
    UUID ownerCustomerId,
    UUID inspectorId,
    Instant plannedStartAt,
    Instant plannedEndAt) {}
