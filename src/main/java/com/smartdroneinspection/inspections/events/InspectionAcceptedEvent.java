package com.smartdroneinspection.inspections.events;

import java.time.Instant;
import java.util.UUID;

/** Published when the assigned Inspector accepts a scheduled inspection assignment. */
public record InspectionAcceptedEvent(
    UUID inspectionId,
    UUID ownerCustomerId,
    UUID inspectorId,
    Instant plannedStartAt,
    Instant acceptedAt) {}
