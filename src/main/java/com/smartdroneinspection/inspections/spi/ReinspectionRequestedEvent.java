package com.smartdroneinspection.inspections.spi;

import java.time.Instant;
import java.util.UUID;

/**
 * MF4-18: Dispatched when an independent reviewer returns REINSPECTION_REQUIRED on a maintenance
 * completion report. Listened by inspections module to spawn a linked verification inspection.
 */
public record ReinspectionRequestedEvent(
    UUID workOrderId,
    UUID reportVersionId,
    UUID organizationId,
    UUID assetId,
    UUID sourceReportVersionId,
    UUID sourceFindingId,
    UUID reviewerUserId,
    String technicalComments,
    Instant requestedAt) {}
