package com.smartdroneinspection.contracts.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Published when an Inspector's net payout is settled manually or via an automated SePay webhook.
 */
public record InspectorPayoutSettledEvent(
    UUID occurrenceId,
    UUID requestId,
    UUID inspectionId,
    UUID inspectorId,
    UUID assignedManagerId,
    String payoutReference,
    BigDecimal netPayoutAmount,
    String payoutMethod,
    boolean automaticWebhook,
    Instant settledAt) {}
