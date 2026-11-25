package com.smartdroneinspection.inspections.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when a Manager approves a submitted report under the two-step gate, unlocking Inspector
 * payout eligibility before customer publication.
 */
public record ReportApprovedForPayoutEvent(
    UUID inspectionId,
    UUID reportVersionId,
    int versionNo,
    UUID inspectorId,
    UUID approvedByUserId,
    Instant approvedAt) {}
