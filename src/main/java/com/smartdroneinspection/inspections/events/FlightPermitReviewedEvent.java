package com.smartdroneinspection.inspections.events;

import java.time.Instant;
import java.util.UUID;

/** Domain event emitted when a Manager approves or rejects a flight permit. */
public record FlightPermitReviewedEvent(
    UUID inspectionId,
    UUID ownerCustomerId,
    UUID inspectorId,
    UUID reviewerUserId,
    boolean approved,
    String rejectionReason,
    Instant reviewedAt) {}
