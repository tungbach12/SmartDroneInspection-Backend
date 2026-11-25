package com.smartdroneinspection.inspections.events;

import java.time.Instant;
import java.util.UUID;

/** Published when a Manager cancels an active inspection. */
public record InspectionCancelledEvent(
    UUID inspectionId,
    UUID ownerCustomerId,
    UUID inspectorId,
    String reason,
    Instant cancelledAt) {}
