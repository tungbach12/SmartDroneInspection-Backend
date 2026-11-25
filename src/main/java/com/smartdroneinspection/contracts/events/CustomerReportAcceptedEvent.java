package com.smartdroneinspection.contracts.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when a Customer formally accepts a published inspection report, starting payment due.
 */
public record CustomerReportAcceptedEvent(
    UUID requestId,
    UUID occurrenceId,
    UUID inspectionId,
    UUID ownerCustomerId,
    UUID assignedManagerId,
    Instant paymentDueAt,
    Instant occurredAt) {}
