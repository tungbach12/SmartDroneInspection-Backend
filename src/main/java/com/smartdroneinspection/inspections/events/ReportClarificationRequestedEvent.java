package com.smartdroneinspection.inspections.events;

import java.time.Instant;
import java.util.UUID;

/** Published when a Customer requests clarification or revision on a published report. */
public record ReportClarificationRequestedEvent(
    UUID inspectionId,
    UUID reportVersionId,
    int versionNo,
    UUID ownerCustomerId,
    UUID inspectorId,
    String reason,
    int clarificationCount,
    Instant requestedAt) {}
