package com.smartdroneinspection.contracts.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Published when an occurrence refund is initiated, completed, or cancelled. */
public record OccurrenceRefundEvent(
    UUID refundId,
    UUID occurrenceId,
    UUID requestId,
    UUID inspectionId,
    UUID ownerCustomerId,
    UUID inspectorId,
    UUID assignedManagerId,
    String eventKind,
    BigDecimal amount,
    String reference,
    String reason,
    Instant occurredAt) {}
