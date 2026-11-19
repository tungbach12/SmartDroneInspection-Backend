package com.smartdroneinspection.contracts.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Emitted after a verified manual or online payment settles an occurrence. Consumed by the
 * notifications module to inform platform Managers and the assigned Inspector.
 */
public record PaymentSettledEvent(
    UUID occurrenceId,
    UUID requestId,
    UUID inspectionId,
    UUID inspectorId,
    String paymentReference,
    BigDecimal amount,
    String paymentMethod,
    Instant settledAt) {}
