package com.smartdroneinspection.maintenance.events;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** MF4-03/04: Dispatched when an independent repair team is assigned to a work order. */
public record WorkOrderAssignedEvent(
    UUID workOrderId,
    UUID organizationId,
    UUID assetId,
    UUID leadUserId,
    UUID reportAuthorUserId,
    UUID acceptingReviewerUserId,
    List<UUID> memberUserIds,
    UUID assignedByUserId,
    Instant assignedAt) {}
