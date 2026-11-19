package com.smartdroneinspection.inspections.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Emitted when a report version is returned to its author for revision. Consumed by the
 * notifications module to alert the assigned Inspector.
 */
public record ReportReturnedEvent(
    UUID inspectionId,
    UUID reportVersionId,
    int versionNo,
    UUID inspectorId,
    UUID returnedByUserId,
    String reason,
    Instant returnedAt) {}
