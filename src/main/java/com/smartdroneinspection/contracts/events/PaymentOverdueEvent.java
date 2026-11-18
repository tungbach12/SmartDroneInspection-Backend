package com.smartdroneinspection.contracts.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Emitted when a customer's accepted report has passed its payment deadline without the occurrence
 * being paid. Consumed by the notifications module to warn the customer and platform managers.
 */
public record PaymentOverdueEvent(
    UUID occurrenceId,
    UUID requestId,
    UUID inspectionId,
    UUID ownerCustomerId,
    Instant paymentDueAt,
    Instant detectedAt) {}
