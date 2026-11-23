package com.smartdroneinspection.contracts.events;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Emitted after a Customer creates a service request. Consumed by the notifications module so
 * active Managers can see the new request waiting for assignment or claim.
 */
public record ServiceRequestCreatedEvent(
    UUID requestId, UUID ownerCustomerId, String description, LocalDate requestedFrom) {}
