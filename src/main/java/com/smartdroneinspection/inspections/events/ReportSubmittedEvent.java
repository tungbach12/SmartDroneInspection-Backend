package com.smartdroneinspection.inspections.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Emitted when an Inspector submits a report version for independent review. Consumed by the
 * notifications module to alert eligible Managers and Admins.
 */
public record ReportSubmittedEvent(
    UUID inspectionId,
    UUID reportVersionId,
    int versionNo,
    UUID submittedByUserId,
    Instant submittedAt) {}
