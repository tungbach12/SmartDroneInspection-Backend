package com.smartdroneinspection.inspections.events;

import java.time.Instant;
import java.util.UUID;

/** Domain event emitted when an on-site field session is aborted by the inspector. */
public record FieldSessionAbortedEvent(
    UUID inspectionId,
    UUID organizationId,
    UUID sessionId,
    UUID inspectorId,
    String abortReason,
    Instant abortedAt) {}
