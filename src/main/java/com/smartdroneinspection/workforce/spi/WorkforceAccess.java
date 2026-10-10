package com.smartdroneinspection.workforce.spi;

import com.smartdroneinspection.workforce.domain.CredentialStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The workforce module's public read boundary.
 *
 * <p>MF4 needs to know whether an engineer's professional credential is currently valid before
 * assigning them to a repair team. Exposing the entity itself would let another module reach
 * workforce internals, so the read is expressed as this record instead.
 */
public interface WorkforceAccess {

  /**
   * Whether the given user currently qualifies, given the credentials held in their organization.
   *
   * <p>A user with no credential record is not blocked: credential verification has never run, so
   * no record exists. A user whose credential exists but is suspended, rejected or expired is
   * refused.
   */
  boolean currentlyQualifies(UUID organizationId, UUID userId);

  /** Read-only view of one credential, for diagnostics and for explaining a refusal. */
  record CredentialView(
      UUID userId, String credentialType, CredentialStatus status, Instant expiresAt) {}

  List<CredentialView> findCredentials(UUID organizationId, UUID userId);
}
