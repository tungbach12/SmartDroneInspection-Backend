package com.smartdroneinspection.inspections.spi;

import java.time.Instant;
import java.util.UUID;

/**
 * Read-only view of a published MF3 repair-required finding made available to MF4.
 *
 * <p>The view contains identifiers and safe display data only; it never exposes an inspection
 * entity, finding entity, repository or evidence bytes across the Modulith boundary.
 */
public record RepairCandidate(
    UUID organizationId,
    UUID assetId,
    UUID inspectionId,
    UUID reportId,
    UUID reportVersionId,
    UUID findingId,
    String findingCode,
    String defectLabel,
    String description,
    String severity,
    String recommendedAction,
    Instant reportPublishedAt) {}
