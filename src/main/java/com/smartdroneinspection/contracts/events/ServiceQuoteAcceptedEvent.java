package com.smartdroneinspection.contracts.events;

import java.time.Instant;
import java.util.UUID;

/** Published when a Customer accepts a service quote. */
public record ServiceQuoteAcceptedEvent(
    UUID requestId,
    UUID ownerCustomerId,
    UUID assignedManagerId,
    String description,
    Instant occurredAt) {}
