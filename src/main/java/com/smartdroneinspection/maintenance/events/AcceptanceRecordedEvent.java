package com.smartdroneinspection.maintenance.events;

import com.smartdroneinspection.maintenance.domain.enums.AcceptanceDecisionKind;
import java.time.Instant;
import java.util.UUID;

/** MF4-18: Dispatched when an independent reviewer records a decision on a completion report. */
public record AcceptanceRecordedEvent(
    UUID workOrderId,
    UUID reportVersionId,
    UUID organizationId,
    UUID assetId,
    UUID reviewerUserId,
    AcceptanceDecisionKind decision,
    String technicalComments,
    UUID teamLeadUserId,
    UUID reportAuthorUserId,
    Instant decidedAt) {}
