package com.smartdroneinspection.inspections.events;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * MF3-13: published inspection report handed to MF4. The payload carries identifiers and scope only
 * — never evidence bytes or secrets. {@code repairRequiredFindingIds} contains only human-confirmed
 * findings the reviewer approved for corrective work, so a no-repair outcome publishes an empty
 * list rather than an empty work order.
 */
public record ReportPublishedEvent(
    UUID reportId,
    UUID reportVersionId,
    UUID inspectionId,
    UUID organizationId,
    UUID assetId,
    UUID publishedByUserId,
    List<UUID> repairRequiredFindingIds,
    Instant publishedAt) {}
