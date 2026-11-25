package com.smartdroneinspection.users.events;

import java.time.Instant;
import java.util.UUID;

/** Published when an Inspector updates onboarding documents (license scan or signed contract). */
public record InspectorOnboardingUpdatedEvent(
    UUID inspectorUserId, String documentType, Instant occurredAt) {}
