package com.smartdroneinspection.inspections.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when a flight permit is uploaded and self-attested by the assigned
 * inspector.
 */
public record FlightPermitSubmittedEvent(
    UUID inspectionId,
    UUID ownerCustomerId,
    UUID inspectorId,
    UUID uploadedByUserId,
    String permitNumber,
    String issuingAuthority,
    Instant submittedAt) {}
