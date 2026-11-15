package com.smartdroneinspection.inspections.events;

import java.time.Instant;
import java.util.UUID;

/** Domain event emitted when a scheduled inspection passes its planned window without starting. */
public record InspectionOverdueEvent(
    UUID inspectionId,
    UUID ownerCustomerId,
    UUID inspectorId,
    Instant plannedEndAt,
    Instant detectedAt) {}
