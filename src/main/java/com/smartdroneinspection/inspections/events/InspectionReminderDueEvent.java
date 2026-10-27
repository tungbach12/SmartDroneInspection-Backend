package com.smartdroneinspection.inspections.events;

import java.time.Instant;
import java.util.UUID;

/** Domain event emitted when an inspection reminder is due for the assigned inspector. */
public record InspectionReminderDueEvent(
    UUID inspectionId, UUID organizationId, UUID inspectorId, Instant plannedStartAt) {}
