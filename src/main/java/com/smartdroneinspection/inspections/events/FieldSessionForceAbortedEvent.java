package com.smartdroneinspection.inspections.events;

import java.time.Instant;
import java.util.UUID;

/** Domain event emitted when a manager or admin force-aborts a hung field session. */
public record FieldSessionForceAbortedEvent(
    UUID inspectionId,
    UUID ownerCustomerId,
    UUID sessionId,
    UUID inspectorId,
    UUID managerId,
    String reason,
    Instant abortedAt) {}
