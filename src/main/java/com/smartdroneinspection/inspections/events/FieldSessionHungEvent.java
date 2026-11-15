package com.smartdroneinspection.inspections.events;

import java.time.Instant;
import java.util.UUID;

/** Domain event emitted when an in-progress field session stays open past its hung threshold. */
public record FieldSessionHungEvent(
    UUID inspectionId,
    UUID ownerCustomerId,
    UUID sessionId,
    UUID inspectorId,
    Instant startedAt,
    Instant plannedEndAt,
    Instant detectedAt) {}
