package com.smartdroneinspection.maintenance.events;

import java.time.Instant;
import java.util.UUID;

/** MF4-18: Dispatched when an independent reviewer records a decision on a completion report. */
public record AcceptanceRecordedEvent(
    UUID workOrderId,
    UUID reportVersionId,
    UUID organizationId,
    UUID assetId,
    UUID reviewerUserId,
    String decision,
    String technicalComments,
    UUID teamLeadUserId,
    UUID reportAuthorUserId,
    Instant decidedAt) {}

