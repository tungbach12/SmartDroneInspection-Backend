package com.smartdroneinspection.contracts.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when an Administrator assigns, reassigns, or unassigns a Manager on a service request.
 */
public record ServiceRequestManagerChangedEvent(
    UUID requestId,
    UUID ownerCustomerId,
    UUID previousManagerId,
    UUID newManagerId,
    String action,
    String requestDescription,
    Instant occurredAt) {}
