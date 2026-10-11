package com.smartdroneinspection.assets.readiness;

import java.time.Instant;
import java.util.UUID;

/** Immutable Drone-document fields required by inspection readiness decisions. */
public record DroneDocumentSummary(
    UUID id,
    UUID droneId,
    String documentType,
    String issuer,
    String documentReference,
    Instant validFrom,
    Instant validUntil,
    DroneDocumentReadinessStatus status,
    UUID reviewedByUserId,
    Instant reviewedAt,
    UUID uploadedByUserId,
    Instant createdAt,
    String checksumSha256) {}
