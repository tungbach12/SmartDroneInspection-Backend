package com.smartdroneinspection.maintenance.events;

import java.time.Instant;
import java.util.UUID;

/** MF4-20: Dispatched when a work order is fully reconciled and closed. */
public record WorkOrderClosedEvent(
    UUID workOrderId, UUID organizationId, UUID assetId, UUID closedByUserId, Instant closedAt) {}
