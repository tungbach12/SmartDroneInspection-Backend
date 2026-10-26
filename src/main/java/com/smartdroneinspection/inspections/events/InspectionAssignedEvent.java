package com.smartdroneinspection.inspections.events;

import java.time.Instant;
import java.util.UUID;

/** Domain event emitted when an inspection is created and assigned to an inspector. */
public record InspectionAssignedEvent(
    UUID inspectionId, UUID organizationId, UUID inspectorId, Instant plannedStartAt) {}
