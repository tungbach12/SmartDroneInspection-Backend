package com.smartdroneinspection.inspections.events;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * MF3-13: published inspection report handed to MF4. The payload carries identifiers and scope only
 * — never evidence bytes or secrets. {@code forwardedFindingIds} contains the confirmed findings
 * the Organization Admin explicitly selected for Maintenance forwarding.
 */
public record ReportPublishedEvent(
    UUID reportId,
    UUID reportVersionId,
    UUID inspectionId,
    UUID organizationId,
    UUID assetId,
    UUID publishedByUserId,
    List<UUID> forwardedFindingIds,
    Instant publishedAt) {}
