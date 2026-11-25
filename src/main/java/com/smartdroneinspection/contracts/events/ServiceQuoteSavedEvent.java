package com.smartdroneinspection.contracts.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Published when a Manager issues or revises a commercial quote for a Customer service request. */
public record ServiceQuoteSavedEvent(
    UUID requestId,
    UUID ownerCustomerId,
    String description,
    boolean revised,
    BigDecimal pricePerOccurrenceVnd,
    String currency,
    Instant occurredAt) {}
