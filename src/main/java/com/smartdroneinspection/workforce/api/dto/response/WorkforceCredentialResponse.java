package com.smartdroneinspection.workforce.api.dto.response;

import com.smartdroneinspection.workforce.domain.WorkforceCredential;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Tenant-scoped credential projection. It reports the expiry-derived status together with the
 * attributable reviewer and reason, and deliberately carries no government-verified flag because
 * the platform never performs such a verification.
 */
public record WorkforceCredentialResponse(
    UUID id,
    UUID userId,
    UUID organizationId,
    String credentialType,
    String issuer,
    String credentialReference,
    Instant issuedAt,
    Instant expiresAt,
    String status,
    UUID evidenceId,
    UUID verifiedByUserId,
    Instant verifiedAt,
    String verificationReason,
    Instant createdAt,
    Instant updatedAt) {

  public static WorkforceCredentialResponse from(WorkforceCredential credential, Instant at) {
    return from(credential, at, WorkforceCredential.DEFAULT_EXPIRING_SOON_WINDOW);
  }

  public static WorkforceCredentialResponse from(
      WorkforceCredential credential, Instant at, Duration expiringSoonWindow) {
    return new WorkforceCredentialResponse(
        credential.getId(),
        credential.getUserId(),
        credential.getOrganizationId(),
        credential.getCredentialType(),
        credential.getIssuer(),
        credential.getCredentialReference(),
        credential.getIssuedAt(),
        credential.getExpiresAt(),
        credential.currentStatus(at, expiringSoonWindow).name(),
        credential.getEvidenceId(),
        credential.getVerifiedByUserId(),
        credential.getVerifiedAt(),
        credential.getVerificationReason(),
        credential.getCreatedAt(),
        credential.getUpdatedAt());
  }
}
