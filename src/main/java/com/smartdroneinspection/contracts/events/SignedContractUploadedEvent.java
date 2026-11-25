package com.smartdroneinspection.contracts.events;

import java.time.Instant;
import java.util.UUID;

/** Published when a Manager uploads the offline signed contract for a service request. */
public record SignedContractUploadedEvent(
    UUID requestId, UUID ownerCustomerId, String description, Instant occurredAt) {}
