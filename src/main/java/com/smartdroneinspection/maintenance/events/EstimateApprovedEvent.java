package com.smartdroneinspection.maintenance.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * MF4-08: Dispatched when an initial estimate baseline is approved by the designated budget
 * approver.
 */
public record EstimateApprovedEvent(
    UUID workOrderId,
    int versionNo,
    UUID organizationId,
    UUID approvedByUserId,
    UUID teamLeadUserId,
    BigDecimal baselineTotal,
    String currency,
    Instant approvedAt) {}
