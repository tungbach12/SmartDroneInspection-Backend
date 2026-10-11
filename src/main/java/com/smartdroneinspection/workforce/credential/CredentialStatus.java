package com.smartdroneinspection.workforce.credential;

/** Credential states exposed by the workforce read contract. */
public enum CredentialStatus {
  DRAFT,
  PENDING_REVIEW,
  ACTIVE,
  EXPIRING_SOON,
  EXPIRED,
  REJECTED,
  SUSPENDED
}
