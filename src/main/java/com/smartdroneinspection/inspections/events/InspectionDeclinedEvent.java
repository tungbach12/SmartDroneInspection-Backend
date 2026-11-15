package com.smartdroneinspection.inspections.events;

import java.time.Instant;
import java.util.UUID;

/** Domain event emitted when an assigned inspector declines a scheduled inspection pre-flight. */
public record InspectionDeclinedEvent(
    UUID inspectionId,
    UUID ownerCustomerId,
    UUID inspectorId,
    String declineReason,
    Instant declinedAt) {}
