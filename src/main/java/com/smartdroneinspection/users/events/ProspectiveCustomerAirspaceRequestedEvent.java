package com.smartdroneinspection.users.events;

import java.time.Instant;
import java.util.UUID;

/** Published when a prospective customer registration requires Admin airspace review. */
public record ProspectiveCustomerAirspaceRequestedEvent(
    UUID registrationId,
    UUID userId,
    String organizationName,
    String assetCode,
    String airspaceStatus,
    Instant occurredAt) {}
