package com.smartdroneinspection.workforce.domain;

/**
 * Credential lifecycle status.
 *
 * <p>Mirrors {@code ck_workforce_credentials_status}. Only {@link #ACTIVE} authorises assignment;
 * {@link #EXPIRING_SOON} is a warning state that still qualifies until the expiry date passes.
 */
public enum CredentialStatus {
  DRAFT,
  PENDING_REVIEW,
  ACTIVE,
  EXPIRING_SOON,
  EXPIRED,
  REJECTED,
  SUSPENDED
}
