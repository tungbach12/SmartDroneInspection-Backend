package com.smartdroneinspection.inspections.events;

import java.time.Instant;
import java.util.UUID;

/** Published whenever a Customer or Manager submits or updates a post-inspection rating. */
public record InspectorRatedEvent(
    UUID ratingId,
    UUID inspectionId,
    UUID inspectorId,
    UUID raterUserId,
    String raterRole,
    int overallScore,
    boolean updated,
    Instant occurredAt) {}
