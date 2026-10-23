package com.smartdroneinspection.maintenance.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** MF4-13: Dispatched when a change order is approved by the budget approver. */
public record ChangeOrderApprovedEvent(
    UUID workOrderId,
    int changeNumber,
    UUID organizationId,
    UUID approvedByUserId,
    UUID requestedByUserId,
    UUID teamLeadUserId,
    BigDecimal proposedDelta,
    Instant decidedAt) {}
