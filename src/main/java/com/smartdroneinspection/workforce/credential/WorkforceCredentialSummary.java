package com.smartdroneinspection.workforce.credential;

import java.time.Instant;
import java.util.UUID;

/** Immutable credential fields needed by readiness decisions. */
public record WorkforceCredentialSummary(
    UUID id,
    UUID organizationId,
    UUID userId,
    String credentialType,
    String issuer,
    String credentialReference,
    Instant issuedAt,
    Instant expiresAt,
    CredentialStatus status,
    UUID evidenceId,
    UUID verifiedByUserId,
    Instant verifiedAt,
    String verificationReason) {}
