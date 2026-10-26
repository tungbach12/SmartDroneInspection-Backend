package com.smartdroneinspection.inspections.api.dto.response;

import java.time.Instant;
import java.util.UUID;

/** What MF2-07 records about one readiness decision on a reviewed preparation version. */
public record ReadinessDecisionResponse(
    UUID id,
    UUID inspectionId,
    UUID preparationId,
    Integer preparationVersion,
    String decision,
    UUID reviewedByUserId,
    String reason,
    Instant decidedAt) {}
