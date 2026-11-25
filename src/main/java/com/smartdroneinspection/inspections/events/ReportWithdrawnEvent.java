package com.smartdroneinspection.inspections.events;

import java.time.Instant;
import java.util.UUID;

/** Published when the authoring Inspector withdraws a submitted report version before review. */
public record ReportWithdrawnEvent(
    UUID inspectionId,
    UUID reportVersionId,
    int versionNo,
    UUID inspectorId,
    Instant withdrawnAt) {}
