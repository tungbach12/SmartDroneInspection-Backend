package com.smartdroneinspection.contracts.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Published when a recovery against an Inspector refund receivable is recorded. */
public record InspectorReceivableRecoveryEvent(
    UUID receivableId,
    UUID occurrenceId,
    UUID requestId,
    UUID inspectorId,
    BigDecimal recoveredAmount,
    String externalReference,
    Instant occurredAt) {}
