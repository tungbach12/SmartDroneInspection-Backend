package com.smartdroneinspection.users.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when an Administrator or Manager approves or rejects an Inspector's onboarding profile.
 */
public record InspectorOnboardingReviewedEvent(
    UUID inspectorUserId, boolean approved, String reason, Instant occurredAt) {}
