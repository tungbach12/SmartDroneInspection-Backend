package com.smartdroneinspection.users.events;

import java.time.Instant;
import java.util.UUID;

/** Published when an Inspector's UAV pilot license metadata or expiration date is updated. */
public record PilotLicenseUpdatedEvent(UUID inspectorUserId, Instant pilotLicenseExpiresAt) {}
