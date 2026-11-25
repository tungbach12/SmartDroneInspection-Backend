package com.smartdroneinspection.users.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when an Administrator approves or rejects a prospective customer airspace registration.
 */
public record ProspectiveCustomerAirspaceReviewedEvent(
    UUID registrationId,
    UUID userId,
    String email,
    String fullName,
    String organizationName,
    String assetCode,
    boolean approved,
    String reviewNote,
    Instant occurredAt) {}
