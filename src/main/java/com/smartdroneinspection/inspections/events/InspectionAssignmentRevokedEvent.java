package com.smartdroneinspection.inspections.events;

import java.time.Instant;
import java.util.UUID;

/** Domain event emitted when an inspector is replaced on an inspection via reassignment. */
public record InspectionAssignmentRevokedEvent(
    UUID inspectionId, UUID previousInspectorId, UUID newInspectorId, Instant revokedAt) {}
