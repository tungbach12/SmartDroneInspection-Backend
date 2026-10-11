package com.smartdroneinspection.assets.readiness;

import java.time.Instant;
import java.util.UUID;

/** Immutable assignment-pair facts required by inspection readiness decisions. */
public record AssetPairReadinessSummary(
    UUID id,
    UUID organizationId,
    UUID assetId,
    UUID inspectorUserId,
    UUID droneId,
    AssetPairReadinessStatus status,
    Instant validFrom,
    Instant validUntil,
    AssignmentReadinessResponse assignmentResponse,
    Instant respondedAt) {}
